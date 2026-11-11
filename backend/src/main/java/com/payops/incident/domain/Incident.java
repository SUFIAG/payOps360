package com.payops.incident.domain;

import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.References;
import com.payops.shared.domain.Severity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Incident aggregate: a customer-impacting problem that groups related alerts
 * and tracks the response from detection to resolution.
 */
public class Incident {

    private final Long id;
    private final long organizationId;
    private final String reference;
    private final String title;
    private String description;
    private Severity severity;
    private IncidentStatus status;
    private final Long providerId;
    private int affectedPayments;
    private BigDecimal affectedAmount;
    private final Instant openedAt;
    private Instant acknowledgedAt;
    private String acknowledgedBy;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNote;
    private AiAnalysis aiAnalysis;
    private SlaClock sla;
    private int escalationLevel;
    private final List<TimelineEntry> newEntries = new ArrayList<>();

    @SuppressWarnings("java:S107")
    public Incident(Long id, long organizationId, String reference, String title, String description, Severity severity,
                    IncidentStatus status, Long providerId, int affectedPayments, BigDecimal affectedAmount,
                    Instant openedAt, Instant acknowledgedAt, String acknowledgedBy, Instant resolvedAt,
                    String resolvedBy, String resolutionNote, AiAnalysis aiAnalysis, SlaClock sla,
                    int escalationLevel) {
        this.id = id;
        this.organizationId = organizationId;
        this.reference = reference;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = status;
        this.providerId = providerId;
        this.affectedPayments = affectedPayments;
        this.affectedAmount = affectedAmount;
        this.openedAt = openedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.acknowledgedBy = acknowledgedBy;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.resolutionNote = resolutionNote;
        this.aiAnalysis = aiAnalysis;
        this.sla = sla == null ? SlaClock.none() : sla;
        this.escalationLevel = escalationLevel;
    }

    public static Incident open(long organizationId, String title, String description, Severity severity,
                                Long providerId, String actor, Instant now, SlaPolicy slaPolicy) {
        SlaPolicy.Target target = slaPolicy.targetFor(severity);
        Incident incident = new Incident(null, organizationId, References.next("INC"), title, description, severity,
                IncidentStatus.OPEN, providerId, 0, BigDecimal.ZERO, now, null, null, null, null, null, null,
                new SlaClock(now.plus(target.ack()), now.plus(target.resolve()), null, null), 0);
        incident.record(TimelineEntry.Kind.OPENED, "Incident opened: " + title, actor, now);
        return incident;
    }

    // ------------------------------------------------------------------ SLA

    /**
     * Re-targets the SLA after a severity change: un-breached deadlines move to
     * the (stricter) target of the new severity, measured from when the
     * incident opened. Deadlines are never relaxed.
     */
    public void retargetSla(SlaPolicy slaPolicy) {
        SlaPolicy.Target target = slaPolicy.targetFor(severity);
        Instant ackDue = sla.ackBreached() ? sla.ackDueAt() : earliest(sla.ackDueAt(), openedAt.plus(target.ack()));
        Instant resolveDue = sla.resolveBreached() ? sla.resolveDueAt()
                : earliest(sla.resolveDueAt(), openedAt.plus(target.resolve()));
        sla = new SlaClock(ackDue, resolveDue, sla.ackBreachedAt(), sla.resolveBreachedAt());
    }

    /**
     * Flags deadlines that passed since the last evaluation and records each
     * breach on the timeline.
     *
     * @return the newly breached SLA kinds (empty when nothing changed)
     */
    public List<SlaClock.Kind> evaluateSla(Instant now) {
        List<SlaClock.Kind> breached = new ArrayList<>();
        Instant ackBreachedAt = sla.ackBreachedAt();
        Instant resolveBreachedAt = sla.resolveBreachedAt();
        if (ackBreachedAt == null && status == IncidentStatus.OPEN && sla.ackDueAt() != null
                && !now.isBefore(sla.ackDueAt())) {
            ackBreachedAt = now;
            breached.add(SlaClock.Kind.ACKNOWLEDGE);
            record(TimelineEntry.Kind.SLA_BREACHED, "Acknowledgement SLA breached: not acknowledged within "
                    + minutesBetween(openedAt, sla.ackDueAt()) + " min", "sla-monitor", now);
        }
        if (resolveBreachedAt == null && status != IncidentStatus.RESOLVED && sla.resolveDueAt() != null
                && !now.isBefore(sla.resolveDueAt())) {
            resolveBreachedAt = now;
            breached.add(SlaClock.Kind.RESOLVE);
            record(TimelineEntry.Kind.SLA_BREACHED, "Resolution SLA breached: not resolved within "
                    + minutesBetween(openedAt, sla.resolveDueAt()) + " min", "sla-monitor", now);
        }
        if (!breached.isEmpty()) {
            sla = new SlaClock(sla.ackDueAt(), sla.resolveDueAt(), ackBreachedAt, resolveBreachedAt);
        }
        return breached;
    }

    /** Records that an escalation policy level was notified. Levels only move up. */
    public void recordEscalation(int level, String message, Instant now) {
        if (level <= escalationLevel) {
            return;
        }
        escalationLevel = level;
        record(TimelineEntry.Kind.ESCALATION_NOTIFIED, message, "escalation-policy", now);
    }

    public void recordPostmortemPublished(String actor, Instant now) {
        record(TimelineEntry.Kind.POSTMORTEM_PUBLISHED, "Postmortem published by " + actor, actor, now);
    }

    private static Instant earliest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return a.isBefore(b) ? a : b;
    }

    private static long minutesBetween(Instant from, Instant to) {
        return Duration.between(from, to).toMinutes();
    }

    public void correlateAlert(String alertReference, Severity alertSeverity, String alertTitle, Instant now) {
        record(TimelineEntry.Kind.ALERT_CORRELATED, "Alert " + alertReference + " correlated: " + alertTitle, "system", now);
        if (alertSeverity.isAtLeast(severity) && alertSeverity != severity) {
            escalate(alertSeverity, "system", now);
        }
    }

    public void acknowledge(String actor, Instant now) {
        requireStatus(IncidentStatus.OPEN, "acknowledged");
        status = IncidentStatus.ACKNOWLEDGED;
        acknowledgedAt = now;
        acknowledgedBy = actor;
        record(TimelineEntry.Kind.ACKNOWLEDGED, "Acknowledged by " + actor, actor, now);
    }

    public void startInvestigation(String actor, Instant now) {
        if (status == IncidentStatus.RESOLVED || status == IncidentStatus.INVESTIGATING) {
            throw new DomainException("INVALID_INCIDENT_STATE", "Incident is already " + status.name().toLowerCase());
        }
        if (acknowledgedAt == null) {
            acknowledgedAt = now;
            acknowledgedBy = actor;
        }
        status = IncidentStatus.INVESTIGATING;
        record(TimelineEntry.Kind.INVESTIGATING, "Investigation started by " + actor, actor, now);
    }

    public void escalate(Severity newSeverity, String actor, Instant now) {
        if (status == IncidentStatus.RESOLVED) {
            throw new DomainException("INCIDENT_RESOLVED", "Resolved incidents cannot be escalated");
        }
        if (!newSeverity.isAtLeast(severity) || newSeverity == severity) {
            throw new DomainException("INVALID_ESCALATION", "Escalation must raise the severity");
        }
        record(TimelineEntry.Kind.ESCALATED, "Severity escalated " + severity + " → " + newSeverity, actor, now);
        severity = newSeverity;
    }

    public void resolve(String actor, String note, Instant now) {
        if (status == IncidentStatus.RESOLVED) {
            throw new DomainException("INCIDENT_RESOLVED", "Incident is already resolved");
        }
        if (note == null || note.isBlank()) {
            throw new DomainException("RESOLUTION_NOTE_REQUIRED", "A resolution note is required");
        }
        status = IncidentStatus.RESOLVED;
        resolvedAt = now;
        resolvedBy = actor;
        resolutionNote = note.trim();
        if (acknowledgedAt == null) {
            acknowledgedAt = now;
            acknowledgedBy = actor;
        }
        record(TimelineEntry.Kind.RESOLVED, "Resolved: " + resolutionNote, actor, now);
    }

    public void addNote(String actor, String note, Instant now) {
        if (note == null || note.isBlank()) {
            throw new DomainException("EMPTY_NOTE", "Note must not be empty");
        }
        record(TimelineEntry.Kind.NOTE, note.trim(), actor, now);
    }

    public void attachAnalysis(AiAnalysis analysis) {
        this.aiAnalysis = analysis;
        record(TimelineEntry.Kind.AI_ANALYSIS, "AI analysis generated (" + analysis.engine() + ")", "ai-copilot",
                analysis.generatedAt());
    }

    public void updateImpact(int payments, BigDecimal amount) {
        this.affectedPayments = payments;
        this.affectedAmount = amount;
    }

    public boolean isActive() {
        return status != IncidentStatus.RESOLVED;
    }

    public Duration timeToAcknowledge() {
        return acknowledgedAt == null ? null : Duration.between(openedAt, acknowledgedAt);
    }

    public Duration timeToResolve() {
        return resolvedAt == null ? null : Duration.between(openedAt, resolvedAt);
    }

    public List<TimelineEntry> pendingTimeline() {
        return Collections.unmodifiableList(newEntries);
    }

    public void clearPendingTimeline() {
        newEntries.clear();
    }

    private void requireStatus(IncidentStatus expected, String action) {
        if (status != expected) {
            throw new DomainException("INVALID_INCIDENT_STATE",
                    "Incident must be " + expected + " to be " + action + " (currently " + status + ")");
        }
    }

    private void record(TimelineEntry.Kind kind, String message, String actor, Instant at) {
        newEntries.add(new TimelineEntry(kind, message, actor, at));
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public String reference() { return reference; }
    public String title() { return title; }
    public String description() { return description; }
    public Severity severity() { return severity; }
    public IncidentStatus status() { return status; }
    public Long providerId() { return providerId; }
    public int affectedPayments() { return affectedPayments; }
    public BigDecimal affectedAmount() { return affectedAmount; }
    public Instant openedAt() { return openedAt; }
    public Instant acknowledgedAt() { return acknowledgedAt; }
    public String acknowledgedBy() { return acknowledgedBy; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolvedBy() { return resolvedBy; }
    public String resolutionNote() { return resolutionNote; }
    public AiAnalysis aiAnalysis() { return aiAnalysis; }
    public SlaClock sla() { return sla; }
    public int escalationLevel() { return escalationLevel; }
}
