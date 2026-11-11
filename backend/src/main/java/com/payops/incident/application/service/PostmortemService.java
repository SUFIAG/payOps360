package com.payops.incident.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.incident.application.port.in.PostmortemUseCase;
import com.payops.incident.application.port.out.IncidentRepository;
import com.payops.incident.application.port.out.PostmortemRepository;
import com.payops.incident.domain.Incident;
import com.payops.incident.domain.Postmortem;
import com.payops.incident.domain.PostmortemDraft;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PostmortemService implements PostmortemUseCase {

    private static final String RESOURCE = "POSTMORTEM";

    private final IncidentRepository incidents;
    private final PostmortemRepository postmortems;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PostmortemView get(long organizationId, String incidentReference) {
        Incident incident = loadIncident(organizationId, incidentReference);
        Postmortem postmortem = postmortems.findByIncident(organizationId, incident.id())
                .orElseGet(() -> Postmortem.draftFor(incident, incidents.timeline(incident.id()), null, clock.instant()));
        return PostmortemView.of(incidentReference, postmortem);
    }

    @Override
    public PostmortemView save(Actor actor, String incidentReference, PostmortemContent content,
                               RequestContext context) {
        Instant now = clock.instant();
        Incident incident = loadIncident(actor.organizationId(), incidentReference);
        Postmortem postmortem = loadOrDraft(actor, incident, now);
        postmortem.edit(content.summary(), content.impact(), content.rootCause(), content.timeline(),
                content.actionItems(), actor.email(), now);
        Postmortem saved = postmortems.save(postmortem);
        if (saved.status() == Postmortem.Status.PUBLISHED) {
            // Changes to the published record are part of the audit trail.
            audit(actor, AuditAction.POSTMORTEM_UPDATED, incidentReference, context, Map.of("status", "PUBLISHED"));
        }
        return PostmortemView.of(incidentReference, saved);
    }

    @Override
    public PostmortemView publish(Actor actor, String incidentReference, RequestContext context) {
        Instant now = clock.instant();
        Incident incident = loadIncident(actor.organizationId(), incidentReference);
        Postmortem postmortem = postmortems.findByIncident(actor.organizationId(), incident.id())
                .orElseThrow(() -> new NotFoundException("Postmortem", incidentReference));
        postmortem.publish(actor.email(), now);
        Postmortem saved = postmortems.save(postmortem);
        incident.recordPostmortemPublished(actor.email(), now);
        incidents.save(incident);
        audit(actor, AuditAction.POSTMORTEM_PUBLISHED, incidentReference, context,
                Map.of("actionItems", String.valueOf(saved.actionItems().size())));
        return PostmortemView.of(incidentReference, saved);
    }

    @Override
    public PostmortemView applyDraft(Actor actor, String incidentReference, PostmortemDraft draft) {
        Instant now = clock.instant();
        Incident incident = loadIncident(actor.organizationId(), incidentReference);
        Postmortem postmortem = loadOrDraft(actor, incident, now);
        postmortem.applyDraft(draft, actor.email(), now);
        return PostmortemView.of(incidentReference, postmortems.save(postmortem));
    }

    private Postmortem loadOrDraft(Actor actor, Incident incident, Instant now) {
        Postmortem.requireResolved(incident);
        return postmortems.findByIncident(actor.organizationId(), incident.id())
                .orElseGet(() -> Postmortem.draftFor(incident, incidents.timeline(incident.id()), actor.email(), now));
    }

    private Incident loadIncident(long organizationId, String reference) {
        return incidents.findByReference(organizationId, reference)
                .orElseThrow(() -> new NotFoundException("Incident", reference));
    }

    private void audit(Actor actor, AuditAction action, String incidentReference, RequestContext context,
                       Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, RESOURCE,
                incidentReference, AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(), details,
                clock.instant()));
    }
}
