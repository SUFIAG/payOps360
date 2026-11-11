package com.payops.notification.application.port.output;

import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.List;

/**
 * Unacknowledged incidents and critical alerts across tenants, and the
 * incident timeline where escalation steps are recorded.
 */
public interface EscalationSubjectsPort {

    List<Subject> awaitingAcknowledgement(int limit);

    void recordIncidentEscalation(long organizationId, String reference, int level, String message);

    /**
     * @param type {@code INCIDENT} or {@code ALERT}
     */
    record Subject(long organizationId, String type, String reference, Severity severity, String title,
                   String providerCode, Instant openedAt) {
    }
}
