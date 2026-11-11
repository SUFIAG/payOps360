package com.payops.incident.application.port.in;

import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.List;

/**
 * System-level port for the escalation engine: finds unacknowledged incidents
 * across tenants and records escalation steps on the incident timeline.
 */
public interface IncidentEscalationUseCase {

    /** Incidents still OPEN (not acknowledged), oldest first. */
    List<AwaitingAcknowledgement> awaitingAcknowledgement(int limit);

    void recordEscalation(long organizationId, String reference, int level, String message);

    record AwaitingAcknowledgement(long organizationId, String reference, Severity severity, String title,
                                   String providerCode, Instant openedAt) {
    }
}
