package com.payops.incident.application.port.input;

import com.payops.incident.domain.AiAnalysis;
import com.payops.incident.domain.Incident;
import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.SlaClock;
import com.payops.shared.domain.Severity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * @param sla             SLA deadlines, breach flags and remaining time (negative once overdue)
 * @param escalationLevel highest escalation-policy level notified so far (0 = none)
 */
public record IncidentView(String reference, String title, String description, Severity severity,
                           IncidentStatus status, String providerCode, int affectedPayments,
                           BigDecimal affectedAmount, Instant openedAt, Instant acknowledgedAt,
                           String acknowledgedBy, Instant resolvedAt, String resolvedBy, String resolutionNote,
                           Long minutesToAcknowledge, Long minutesToResolve, AiAnalysis aiAnalysis, Sla sla,
                           int escalationLevel) {

    /**
     * @param ackRemainingSeconds     seconds until the acknowledge deadline; null once acknowledged
     * @param resolveRemainingSeconds seconds until the resolve deadline; null once resolved
     */
    public record Sla(Instant ackDueAt, Instant resolveDueAt, boolean ackBreached, boolean resolveBreached,
                      Long ackRemainingSeconds, Long resolveRemainingSeconds) {
    }

    public static IncidentView of(Incident i, String providerCode, Instant now) {
        Duration tta = i.timeToAcknowledge();
        Duration ttr = i.timeToResolve();
        return new IncidentView(i.reference(), i.title(), i.description(), i.severity(), i.status(), providerCode,
                i.affectedPayments(), i.affectedAmount(), i.openedAt(), i.acknowledgedAt(), i.acknowledgedBy(),
                i.resolvedAt(), i.resolvedBy(), i.resolutionNote(), tta == null ? null : tta.toMinutes(),
                ttr == null ? null : ttr.toMinutes(), i.aiAnalysis(), sla(i, now), i.escalationLevel());
    }

    private static Sla sla(Incident i, Instant now) {
        SlaClock c = i.sla();
        boolean ackPending = i.acknowledgedAt() == null && i.status() != IncidentStatus.RESOLVED;
        boolean resolvePending = i.status() != IncidentStatus.RESOLVED;
        // A deadline counts as breached once passed, even before the SLA job flags it.
        boolean ackBreached = c.ackBreached() || ackPending && c.ackDueAt() != null && now.isAfter(c.ackDueAt());
        boolean resolveBreached = c.resolveBreached()
                || resolvePending && c.resolveDueAt() != null && now.isAfter(c.resolveDueAt());
        return new Sla(c.ackDueAt(), c.resolveDueAt(), ackBreached, resolveBreached,
                ackPending && c.ackDueAt() != null ? Duration.between(now, c.ackDueAt()).toSeconds() : null,
                resolvePending && c.resolveDueAt() != null ? Duration.between(now, c.resolveDueAt()).toSeconds() : null);
    }
}
