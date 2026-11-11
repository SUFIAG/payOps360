package com.payops.incident.application.port.in;

import com.payops.incident.domain.Postmortem;
import com.payops.incident.domain.PostmortemDraft;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.time.Instant;
import java.util.List;

/**
 * Postmortems of resolved incidents.
 */
public interface PostmortemUseCase {

    /**
     * The stored postmortem, or an unsaved draft prefilled from the incident
     * ({@link PostmortemView#exists()} false) when none has been written yet.
     */
    PostmortemView get(long organizationId, String incidentReference);

    PostmortemView save(Actor actor, String incidentReference, PostmortemContent content, RequestContext context);

    PostmortemView publish(Actor actor, String incidentReference, RequestContext context);

    /** Merges a generated draft into the (possibly new) DRAFT postmortem. */
    PostmortemView applyDraft(Actor actor, String incidentReference, PostmortemDraft draft);

    record PostmortemContent(String summary, String impact, String rootCause, List<Postmortem.TimelineItem> timeline,
                             List<Postmortem.ActionItem> actionItems) {
    }

    record PostmortemView(String incidentReference, boolean exists, Postmortem.Status status, String summary,
                          String impact, String rootCause, List<Postmortem.TimelineItem> timeline,
                          List<Postmortem.ActionItem> actionItems, String aiEngine, String createdBy,
                          String updatedBy, String publishedBy, Instant createdAt, Instant updatedAt,
                          Instant publishedAt) {

        public static PostmortemView of(String incidentReference, Postmortem p) {
            return new PostmortemView(incidentReference, p.id() != null, p.status(), p.summary(), p.impact(),
                    p.rootCause(), p.timeline(), p.actionItems(), p.aiEngine(), p.createdBy(), p.updatedBy(),
                    p.publishedBy(), p.createdAt(), p.updatedAt(), p.publishedAt());
        }
    }
}
