package com.payops.incident.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.incident.application.port.in.IncidentCommandUseCase;
import com.payops.incident.application.port.in.IncidentCorrelationUseCase;
import com.payops.incident.application.port.in.IncidentEscalationUseCase;
import com.payops.incident.application.port.in.IncidentQueryUseCase;
import com.payops.incident.application.port.in.IncidentSlaUseCase;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.incident.application.port.out.IncidentContextPort;
import com.payops.incident.application.port.out.IncidentRepository;
import com.payops.incident.application.port.out.SlaPolicyRepository;
import com.payops.incident.domain.AiAnalysis;
import com.payops.incident.domain.Incident;
import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.SlaClock;
import com.payops.incident.domain.SlaPolicy;
import com.payops.shared.application.Actor;
import com.payops.shared.application.LiveEventPublisher;
import com.payops.shared.application.OperationalEvent;
import com.payops.shared.application.OperationalEventPublisher;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService implements IncidentQueryUseCase, IncidentCommandUseCase, IncidentCorrelationUseCase,
        IncidentSlaUseCase, IncidentEscalationUseCase {

    static final Duration CORRELATION_WINDOW = Duration.ofHours(2);
    private static final int SLA_BATCH = 200;
    private static final String SUBJECT = "INCIDENT";

    private final IncidentRepository incidents;
    private final SlaPolicyRepository slaPolicies;
    private final IncidentContextPort context;
    private final LiveEventPublisher liveEvents;
    private final OperationalEventPublisher operationalEvents;
    private final AuditTrail auditTrail;
    private final Clock clock;

    // ------------------------------------------------------------- correlation

    @Override
    public long correlate(long organizationId, Long providerId, Severity severity, String alertReference, String title,
                          String description) {
        Instant now = clock.instant();
        Incident incident = incidents.findActiveForProvider(organizationId, providerId, now.minus(CORRELATION_WINDOW))
                .orElse(null);
        boolean created = incident == null;
        SlaPolicy policy = slaPolicy(organizationId);
        if (created) {
            String providerLabel = providerId == null ? null : context.providerCode(organizationId, providerId).orElse(null);
            String incidentTitle = providerLabel == null ? title : providerLabel + ": " + title;
            incident = Incident.open(organizationId, incidentTitle, description, severity, providerId, "system", now,
                    policy);
        }
        Severity before = incident.severity();
        incident.correlateAlert(alertReference, severity, title, now);
        if (incident.severity() != before) {
            incident.retargetSla(policy);
        }
        refreshImpact(incident);
        Incident saved = incidents.save(incident);
        IncidentView view = view(saved);
        liveEvents.publish(organizationId, created ? "incident.opened" : "incident.updated", view);
        if (created) {
            auditTrail.record(new AuditEntry(organizationId, null, "system", AuditAction.INCIDENT_CREATED, "INCIDENT",
                    saved.reference(), AuditOutcome.SUCCESS, null, null, Map.of("alert", alertReference), now));
            emit(saved, view, OperationalEvent.Type.INCIDENT_OPENED, "Incident opened from alert " + alertReference, null);
        } else if (saved.severity() != before) {
            emitEscalated(saved, view, before);
        }
        return saved.id();
    }

    @Override
    @Transactional(readOnly = true)
    public String referenceOf(long organizationId, long incidentId) {
        return incidents.findById(organizationId, incidentId).map(Incident::reference).orElse(null);
    }

    // ---------------------------------------------------------------- commands

    @Override
    public IncidentView declare(Actor actor, String title, String description, Severity severity, String providerCode,
                                RequestContext requestContext) {
        Long providerId = providerCode == null || providerCode.isBlank() ? null
                : context.providerId(actor.organizationId(), providerCode)
                .orElseThrow(() -> new NotFoundException("Provider", providerCode));
        Incident incident = Incident.open(actor.organizationId(), title, description, severity, providerId,
                actor.email(), clock.instant(), slaPolicy(actor.organizationId()));
        refreshImpact(incident);
        Incident saved = incidents.save(incident);
        audit(actor, AuditAction.INCIDENT_CREATED, saved, requestContext, Map.of("severity", severity.name()));
        IncidentView view = publish(saved, "incident.opened");
        emit(saved, view, OperationalEvent.Type.INCIDENT_OPENED, "Declared by " + actor.email(), null);
        return view;
    }

    @Override
    public IncidentView acknowledge(Actor actor, String reference, RequestContext requestContext) {
        Incident incident = load(actor.organizationId(), reference);
        incident.acknowledge(actor.email(), clock.instant());
        return statusChanged(actor, incident, requestContext);
    }

    @Override
    public IncidentView investigate(Actor actor, String reference, RequestContext requestContext) {
        Incident incident = load(actor.organizationId(), reference);
        incident.startInvestigation(actor.email(), clock.instant());
        return statusChanged(actor, incident, requestContext);
    }

    @Override
    public IncidentView escalate(Actor actor, String reference, Severity severity, RequestContext requestContext) {
        Incident incident = load(actor.organizationId(), reference);
        Severity before = incident.severity();
        incident.escalate(severity, actor.email(), clock.instant());
        incident.retargetSla(slaPolicy(actor.organizationId()));
        IncidentView view = statusChanged(actor, incident, requestContext);
        emitEscalated(incident, view, before);
        return view;
    }

    @Override
    public IncidentView resolve(Actor actor, String reference, String note, RequestContext requestContext) {
        Incident incident = load(actor.organizationId(), reference);
        refreshImpact(incident);
        incident.resolve(actor.email(), note, clock.instant());
        IncidentView view = statusChanged(actor, incident, requestContext);
        emit(incident, view, OperationalEvent.Type.INCIDENT_RESOLVED, "Resolved by " + actor.email() + ": "
                + incident.resolutionNote(), null);
        return view;
    }

    @Override
    public IncidentView addNote(Actor actor, String reference, String note) {
        Incident incident = load(actor.organizationId(), reference);
        incident.addNote(actor.email(), note, clock.instant());
        return publish(incidents.save(incident), "incident.updated");
    }

    @Override
    public IncidentView recordAnalysis(long organizationId, String reference, AiAnalysis analysis) {
        Incident incident = load(organizationId, reference);
        incident.attachAnalysis(analysis);
        return publish(incidents.save(incident), "incident.updated");
    }

    // ----------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public PageResult<IncidentView> list(long organizationId, IncidentStatus status, PageQuery page) {
        return incidents.search(organizationId, status, page).map(this::view);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentDetails get(long organizationId, String reference) {
        Incident incident = load(organizationId, reference);
        return new IncidentDetails(view(incident), incidents.timeline(incident.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentView> active(long organizationId) {
        return incidents.findActive(organizationId).stream()
                .sorted(Comparator.comparing(Incident::severity).reversed().thenComparing(Incident::openedAt))
                .map(this::view)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summary(long organizationId) {
        Instant now = clock.instant();
        Map<IncidentStatus, Long> counts = incidents.countByStatus(organizationId);
        List<Incident> resolved = incidents.findResolvedSince(organizationId, now.minus(Duration.ofDays(30)));
        OptionalDouble mtta = resolved.stream().map(Incident::timeToAcknowledge).filter(Objects::nonNull)
                .mapToLong(Duration::toMinutes).average();
        OptionalDouble mttr = resolved.stream().map(Incident::timeToResolve).filter(Objects::nonNull)
                .mapToLong(Duration::toMinutes).average();
        long activeBreaches = incidents.findActive(organizationId).stream()
                .map(i -> IncidentView.of(i, null, now).sla())
                .filter(s -> s.ackBreached() || s.resolveBreached())
                .count();
        Double attainment = resolved.isEmpty() ? null
                : resolved.stream().filter(i -> !i.sla().anyBreached()).count() * 100.0 / resolved.size();
        return new Summary(counts.getOrDefault(IncidentStatus.OPEN, 0L),
                counts.getOrDefault(IncidentStatus.ACKNOWLEDGED, 0L),
                counts.getOrDefault(IncidentStatus.INVESTIGATING, 0L),
                resolved.size(),
                mtta.isPresent() ? mtta.getAsDouble() : null,
                mttr.isPresent() ? mttr.getAsDouble() : null,
                activeBreaches,
                attainment);
    }

    // --------------------------------------------------------------------- SLA

    @Override
    @Transactional(readOnly = true)
    public List<SlaTargetView> slaTargets(long organizationId) {
        Map<Severity, SlaPolicy.Target> overrides = slaPolicies.overrides(organizationId);
        SlaPolicy policy = SlaPolicy.of(overrides);
        return Arrays.stream(Severity.values()).sorted(Comparator.reverseOrder())
                .map(s -> new SlaTargetView(s, policy.targetFor(s).ackMinutes(), policy.targetFor(s).resolveMinutes(),
                        overrides.containsKey(s)))
                .toList();
    }

    @Override
    public List<SlaTargetView> updateSlaTargets(Actor actor, Map<Severity, SlaPolicy.Target> targets,
                                                RequestContext requestContext) {
        Map<Severity, SlaPolicy.Target> overrides = new HashMap<>(slaPolicies.overrides(actor.organizationId()));
        overrides.putAll(targets);
        slaPolicies.save(actor.organizationId(), overrides, actor.email(), clock.instant());
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(),
                AuditAction.INCIDENT_SLA_POLICY_UPDATED, "SLA_POLICY", String.valueOf(actor.organizationId()),
                AuditOutcome.SUCCESS, requestContext.ipAddress(), requestContext.userAgent(),
                targets.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name(),
                        e -> e.getValue().ackMinutes() + "m/" + e.getValue().resolveMinutes() + "m")),
                clock.instant()));
        return slaTargets(actor.organizationId());
    }

    @Override
    public int flagSlaBreaches() {
        Instant now = clock.instant();
        int flagged = 0;
        for (Incident incident : incidents.findSlaDue(now, SLA_BATCH)) {
            List<SlaClock.Kind> breaches = incident.evaluateSla(now);
            if (breaches.isEmpty()) {
                continue;
            }
            Incident saved = incidents.save(incident);
            IncidentView view = publish(saved, "incident.updated");
            for (SlaClock.Kind kind : breaches) {
                String what = kind == SlaClock.Kind.ACKNOWLEDGE ? "acknowledge" : "resolve";
                emit(saved, view, OperationalEvent.Type.INCIDENT_SLA_BREACHED,
                        "The " + what + " SLA for this " + saved.severity() + " incident was breached",
                        "INCIDENT_SLA_BREACHED:" + saved.reference() + ":" + kind.name());
            }
            flagged++;
        }
        return flagged;
    }

    // -------------------------------------------------------------- escalation

    @Override
    @Transactional(readOnly = true)
    public List<AwaitingAcknowledgement> awaitingAcknowledgement(int limit) {
        return incidents.findAwaitingAcknowledgement(limit).stream()
                .map(i -> new AwaitingAcknowledgement(i.organizationId(), i.reference(), i.severity(), i.title(),
                        providerCode(i), i.openedAt()))
                .toList();
    }

    @Override
    public void recordEscalation(long organizationId, String reference, int level, String message) {
        Incident incident = load(organizationId, reference);
        int before = incident.escalationLevel();
        incident.recordEscalation(level, message, clock.instant());
        if (incident.escalationLevel() != before) {
            publish(incidents.save(incident), "incident.updated");
        }
    }

    // ------------------------------------------------------------------------

    private SlaPolicy slaPolicy(long organizationId) {
        return SlaPolicy.of(slaPolicies.overrides(organizationId));
    }

    private void refreshImpact(Incident incident) {
        IncidentContextPort.Impact impact = context.impactSince(incident.organizationId(), incident.providerId(),
                incident.openedAt().minus(Duration.ofMinutes(15)));
        incident.updateImpact(impact.payments(), impact.amount());
    }

    private IncidentView statusChanged(Actor actor, Incident incident, RequestContext requestContext) {
        Incident saved = incidents.save(incident);
        audit(actor, AuditAction.INCIDENT_STATUS_CHANGED, saved, requestContext, Map.of("status", saved.status().name()));
        return publish(saved, "incident.updated");
    }

    private IncidentView publish(Incident incident, String event) {
        IncidentView view = view(incident);
        liveEvents.publish(incident.organizationId(), event, view);
        return view;
    }

    private void emitEscalated(Incident incident, IncidentView view, Severity before) {
        emit(incident, view, OperationalEvent.Type.INCIDENT_ESCALATED,
                "Severity escalated " + before + " -> " + incident.severity(),
                "INCIDENT_ESCALATED:" + incident.reference() + ":" + incident.severity());
    }

    private void emit(Incident incident, IncidentView view, OperationalEvent.Type type, String detail, String dedupKey) {
        operationalEvents.publish(new OperationalEvent(incident.organizationId(), type, incident.severity(), SUBJECT,
                incident.reference(), view.providerCode(), incident.title(), detail, dedupKey, clock.instant()));
    }

    private Incident load(long organizationId, String reference) {
        return incidents.findByReference(organizationId, reference)
                .orElseThrow(() -> new NotFoundException("Incident", reference));
    }

    private String providerCode(Incident incident) {
        return incident.providerId() == null ? null
                : context.providerCode(incident.organizationId(), incident.providerId()).orElse(null);
    }

    private IncidentView view(Incident incident) {
        return IncidentView.of(incident, providerCode(incident), clock.instant());
    }

    private void audit(Actor actor, AuditAction action, Incident incident, RequestContext requestContext,
                       Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, "INCIDENT",
                incident.reference(), AuditOutcome.SUCCESS, requestContext.ipAddress(), requestContext.userAgent(),
                details, clock.instant()));
    }
}
