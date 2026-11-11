package com.payops.incident.domain;

import com.payops.shared.domain.DomainException;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Blameless post-incident review of a resolved incident: what happened, the
 * impact, the root cause, the timeline and follow-up action items. Starts as a
 * DRAFT; publishing makes it the record of the incident (action items can
 * still be tracked afterwards).
 */
public class Postmortem {

    public enum Status { DRAFT, PUBLISHED }

    public record TimelineItem(Instant at, String text) {
    }

    public record ActionItem(String text, String owner, LocalDate dueDate, boolean done) {

        public ActionItem {
            if (text == null || text.isBlank()) {
                throw new DomainException("INVALID_ACTION_ITEM", "Action items need a description");
            }
            text = text.trim();
            owner = owner == null || owner.isBlank() ? null : owner.trim();
        }
    }

    static final int MAX_TEXT = 20_000;
    static final int MAX_ITEMS = 100;

    private final Long id;
    private final long organizationId;
    private final long incidentId;
    private Status status;
    private String summary;
    private String impact;
    private String rootCause;
    private List<TimelineItem> timeline;
    private List<ActionItem> actionItems;
    private String aiEngine;
    private final String createdBy;
    private String updatedBy;
    private String publishedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    @SuppressWarnings("java:S107")
    public Postmortem(Long id, long organizationId, long incidentId, Status status, String summary, String impact,
                      String rootCause, List<TimelineItem> timeline, List<ActionItem> actionItems, String aiEngine,
                      String createdBy, String updatedBy, String publishedBy, Instant createdAt, Instant updatedAt,
                      Instant publishedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.incidentId = incidentId;
        this.status = status;
        this.summary = summary;
        this.impact = impact;
        this.rootCause = rootCause;
        this.timeline = timeline == null ? List.of() : List.copyOf(timeline);
        this.actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
        this.aiEngine = aiEngine;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.publishedBy = publishedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
    }

    /**
     * A new draft prefilled from the incident: the resolution and any AI
     * analysis seed the narrative, the incident timeline seeds the review timeline.
     */
    public static Postmortem draftFor(Incident incident, List<TimelineEntry> incidentTimeline, String actor,
                                      Instant now) {
        requireResolved(incident);
        AiAnalysis ai = incident.aiAnalysis();
        String summary = ai != null ? ai.summary()
                : incident.severity() + " incident \"" + incident.title() + "\" was resolved: " + incident.resolutionNote();
        String impact = impactStatement(incident);
        String rootCause = ai != null ? ai.rootCause() : null;
        List<TimelineItem> timeline = incidentTimeline.stream()
                .map(t -> new TimelineItem(t.occurredAt(), label(t.kind()) + ": " + t.message()))
                .toList();
        List<ActionItem> items = ai == null ? List.of()
                : ai.recommendations().stream().limit(10).map(r -> new ActionItem(r, null, null, false)).toList();
        return new Postmortem(null, incident.organizationId(), incident.id(), Status.DRAFT, summary, impact, rootCause,
                timeline, items, null, actor, actor, null, now, now, null);
    }

    public static void requireResolved(Incident incident) {
        if (incident.status() != IncidentStatus.RESOLVED) {
            throw new DomainException("INCIDENT_NOT_RESOLVED", "Postmortems can only be written for resolved incidents");
        }
    }

    public void edit(String summary, String impact, String rootCause, List<TimelineItem> timeline,
                     List<ActionItem> actionItems, String actor, Instant now) {
        this.summary = clean(summary);
        this.impact = clean(impact);
        this.rootCause = clean(rootCause);
        this.timeline = limit(timeline == null ? List.of() : timeline.stream()
                .filter(t -> t != null && t.text() != null && !t.text().isBlank()).toList());
        this.actionItems = limit(actionItems == null ? List.of() : actionItems);
        touch(actor, now);
    }

    /** Merges an AI (or deterministic) draft into a DRAFT postmortem. Existing action items are kept. */
    public void applyDraft(PostmortemDraft draft, String actor, Instant now) {
        if (status == Status.PUBLISHED) {
            throw new DomainException("POSTMORTEM_PUBLISHED", "A published postmortem cannot be redrafted");
        }
        summary = clean(draft.summary());
        impact = clean(draft.impact());
        rootCause = clean(draft.rootCause());
        List<ActionItem> merged = new ArrayList<>(actionItems);
        for (PostmortemDraft.Item item : draft.actionItems()) {
            if (item.text() == null || item.text().isBlank()) {
                continue;
            }
            boolean exists = merged.stream().anyMatch(a -> a.text().equalsIgnoreCase(item.text().trim()));
            if (!exists) {
                merged.add(new ActionItem(item.text(), item.owner(), null, false));
            }
        }
        actionItems = limit(merged);
        aiEngine = draft.engine();
        touch(actor, now);
    }

    public void publish(String actor, Instant now) {
        if (status == Status.PUBLISHED) {
            throw new DomainException("POSTMORTEM_PUBLISHED", "Postmortem is already published");
        }
        if (summary == null || rootCause == null) {
            throw new DomainException("POSTMORTEM_INCOMPLETE", "Summary and root cause are required before publishing");
        }
        status = Status.PUBLISHED;
        publishedAt = now;
        publishedBy = actor;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        updatedBy = actor;
        updatedAt = now;
    }

    private static String impactStatement(Incident incident) {
        StringBuilder sb = new StringBuilder();
        sb.append(incident.affectedPayments()).append(" payment(s) affected, about ")
                .append(incident.affectedAmount().setScale(0, RoundingMode.HALF_UP).toPlainString())
                .append(" at risk.");
        if (incident.timeToAcknowledge() != null) {
            sb.append(" Acknowledged after ").append(incident.timeToAcknowledge().toMinutes()).append(" min");
        }
        if (incident.timeToResolve() != null) {
            sb.append(", resolved after ").append(incident.timeToResolve().toMinutes()).append(" min.");
        }
        if (incident.sla().anyBreached()) {
            sb.append(" SLA breached (")
                    .append(incident.sla().ackBreached() ? "acknowledge" : "")
                    .append(incident.sla().ackBreached() && incident.sla().resolveBreached() ? ", " : "")
                    .append(incident.sla().resolveBreached() ? "resolve" : "")
                    .append(").");
        }
        return sb.toString();
    }

    private static String label(TimelineEntry.Kind kind) {
        String text = kind.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_TEXT ? trimmed : trimmed.substring(0, MAX_TEXT);
    }

    private static <T> List<T> limit(List<T> items) {
        if (items.size() > MAX_ITEMS) {
            throw new DomainException("POSTMORTEM_TOO_LARGE", "At most " + MAX_ITEMS + " entries are allowed");
        }
        return List.copyOf(items);
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public long incidentId() { return incidentId; }
    public Status status() { return status; }
    public String summary() { return summary; }
    public String impact() { return impact; }
    public String rootCause() { return rootCause; }
    public List<TimelineItem> timeline() { return timeline; }
    public List<ActionItem> actionItems() { return actionItems; }
    public String aiEngine() { return aiEngine; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public String publishedBy() { return publishedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant publishedAt() { return publishedAt; }
}
