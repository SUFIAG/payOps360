package com.payops.shared.application;

import com.payops.shared.domain.Severity;

import java.time.Instant;

/**
 * A business-significant operational event (alert raised, incident opened,
 * SLA breached ...) that other modules may react to, e.g. to notify on-call
 * channels. Published through {@link OperationalEventPublisher}.
 *
 * @param subjectType      {@code ALERT}, {@code INCIDENT} or {@code PROVIDER}
 * @param subjectReference business reference of the subject (alert/incident reference, provider code)
 * @param dedupKey         stable key identifying "the same" event for notification de-duplication
 */
public record OperationalEvent(long organizationId, Type type, Severity severity, String subjectType,
                               String subjectReference, String providerCode, String title, String detail,
                               String dedupKey, Instant occurredAt) {

    public enum Type {
        ALERT_RAISED,
        INCIDENT_OPENED,
        INCIDENT_ESCALATED,
        INCIDENT_SLA_BREACHED,
        INCIDENT_RESOLVED,
        PROVIDER_DEGRADED,
        /** A settlement batch produced reconciliation exceptions at or above the configured threshold. */
        RECONCILIATION_EXCEPTIONS
    }

    public OperationalEvent {
        if (type == null || severity == null || subjectReference == null) {
            throw new IllegalArgumentException("type, severity and subjectReference are required");
        }
        if (dedupKey == null || dedupKey.isBlank()) {
            dedupKey = type.name() + ":" + subjectReference;
        }
    }

    public static OperationalEvent of(long organizationId, Type type, Severity severity, String subjectType,
                                      String subjectReference, String providerCode, String title, String detail,
                                      Instant occurredAt) {
        return new OperationalEvent(organizationId, type, severity, subjectType, subjectReference, providerCode, title,
                detail, null, occurredAt);
    }

    public OperationalEvent withDedupKey(String key) {
        return new OperationalEvent(organizationId, type, severity, subjectType, subjectReference, providerCode, title,
                detail, key, occurredAt);
    }
}
