package com.payops.alert.application.service;

import com.payops.alert.application.port.in.AlertCommandUseCase;
import com.payops.alert.application.port.in.AlertQueryUseCase;
import com.payops.alert.application.port.in.AlertView;
import com.payops.alert.application.port.in.RaiseAlertUseCase;
import com.payops.alert.application.port.out.AlertContextPort;
import com.payops.alert.application.port.out.AlertRepository;
import com.payops.alert.domain.Alert;
import com.payops.alert.domain.AlertStatus;
import com.payops.alert.domain.AlertType;
import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AlertService implements AlertQueryUseCase, AlertCommandUseCase, RaiseAlertUseCase {

    /** Alerts quiet for this long are auto-resolved. */
    static final Duration QUIET_PERIOD = Duration.ofMinutes(15);
    private static final int AUTO_RESOLVE_BATCH = 500;

    private final AlertRepository alerts;
    private final AlertContextPort context;
    private final LiveEventPublisher liveEvents;
    private final OperationalEventPublisher operationalEvents;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    public AlertView raise(RaiseAlertCommand c) {
        Instant now = clock.instant();
        Optional<Alert> open = alerts.findUnresolvedByFingerprint(c.organizationId(), c.fingerprint());
        if (open.isPresent()) {
            Alert alert = open.get();
            Severity before = alert.severity();
            alert.recur(c.severity(), c.metricValue(), c.description(), now);
            Alert saved = alerts.save(alert);
            if (saved.severity() != before) {
                correlate(saved);
                liveEvents.publish(c.organizationId(), "alert.updated", view(saved));
            }
            return view(saved);
        }
        Alert alert = alerts.save(Alert.raise(c.organizationId(), c.type(), c.severity(), c.source(), c.title(),
                c.description(), c.providerId(), c.paymentReference(), c.metricValue(), c.threshold(),
                c.fingerprint(), now));
        correlate(alert);
        AlertView view = view(alerts.save(alert));
        liveEvents.publish(c.organizationId(), "alert.raised", view);
        operationalEvents.publish(OperationalEvent.of(c.organizationId(), OperationalEvent.Type.ALERT_RAISED,
                view.severity(), "ALERT", view.reference(), view.providerCode(), view.title(), view.description(), now));
        return view;
    }

    @Override
    public int autoResolveQuietAlerts() {
        Instant now = clock.instant();
        int resolved = 0;
        for (Alert alert : alerts.findUnresolvedNotSeenSince(now.minus(QUIET_PERIOD), AUTO_RESOLVE_BATCH)) {
            if (alert.type() != AlertType.HIGH_RISK_PAYMENT && alert.autoResolveIfQuiet(QUIET_PERIOD, now)) {
                alerts.save(alert);
                liveEvents.publish(alert.organizationId(), "alert.updated", view(alert));
                resolved++;
            }
        }
        return resolved;
    }

    @Override
    public AlertView acknowledge(Actor actor, String reference, RequestContext requestContext) {
        Alert alert = load(actor.organizationId(), reference);
        alert.acknowledge(actor.email(), clock.instant());
        AlertView view = view(alerts.save(alert));
        audit(actor, AuditAction.ALERT_ACKNOWLEDGED, alert, requestContext);
        liveEvents.publish(actor.organizationId(), "alert.updated", view);
        return view;
    }

    @Override
    public AlertView resolve(Actor actor, String reference, String note, RequestContext requestContext) {
        Alert alert = load(actor.organizationId(), reference);
        alert.resolve(actor.email(), note, clock.instant());
        AlertView view = view(alerts.save(alert));
        audit(actor, AuditAction.ALERT_RESOLVED, alert, requestContext);
        liveEvents.publish(actor.organizationId(), "alert.updated", view);
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AlertView> list(long organizationId, AlertStatus status, Severity severity, PageQuery page) {
        return alerts.search(organizationId, status, severity, page).map(this::view);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertView get(long organizationId, String reference) {
        return view(load(organizationId, reference));
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summary(long organizationId) {
        Map<AlertStatus, Long> byStatus = alerts.countByStatus(organizationId);
        return new Summary(byStatus.getOrDefault(AlertStatus.OPEN, 0L),
                byStatus.getOrDefault(AlertStatus.ACKNOWLEDGED, 0L),
                alerts.countResolvedSince(organizationId, clock.instant().minus(Duration.ofHours(24))),
                alerts.countUnresolvedBySeverity(organizationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AwaitingAcknowledgement> awaitingAcknowledgement(Severity minSeverity, int limit) {
        return alerts.findAwaitingAcknowledgement(minSeverity, limit).stream()
                .map(a -> new AwaitingAcknowledgement(a.organizationId(), a.reference(), a.severity(), a.title(),
                        a.providerId() == null ? null
                                : context.providerCode(a.organizationId(), a.providerId()).orElse(null),
                        a.detectedAt()))
                .toList();
    }

    private void correlate(Alert alert) {
        if (alert.incidentId() == null && alert.severity().isAtLeast(Severity.HIGH)) {
            context.correlateIncident(alert.organizationId(), alert.providerId(), alert.severity(), alert.reference(),
                    alert.title(), alert.description()).ifPresent(alert::linkIncident);
        }
    }

    private Alert load(long organizationId, String reference) {
        return alerts.findByReference(organizationId, reference).orElseThrow(() -> new NotFoundException("Alert", reference));
    }

    private AlertView view(Alert alert) {
        String providerCode = alert.providerId() == null ? null
                : context.providerCode(alert.organizationId(), alert.providerId()).orElse(null);
        String incidentRef = alert.incidentId() == null ? null
                : context.incidentReference(alert.organizationId(), alert.incidentId()).orElse(null);
        return AlertView.of(alert, providerCode, incidentRef);
    }

    private void audit(Actor actor, AuditAction action, Alert alert, RequestContext requestContext) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, "ALERT",
                alert.reference(), AuditOutcome.SUCCESS, requestContext.ipAddress(), requestContext.userAgent(),
                Map.of(), clock.instant()));
    }
}
