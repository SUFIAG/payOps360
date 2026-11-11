package com.payops.incident.domain;

import java.time.Instant;

public record TimelineEntry(Kind kind, String message, String actor, Instant occurredAt) {

    public enum Kind {
        OPENED, ALERT_CORRELATED, ACKNOWLEDGED, INVESTIGATING, NOTE, ESCALATED, AI_ANALYSIS, RESOLVED,
        SLA_BREACHED, ESCALATION_NOTIFIED, POSTMORTEM_PUBLISHED
    }
}
