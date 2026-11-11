package com.payops.incident.application.port.in;

import com.payops.shared.domain.Severity;

/**
 * Groups related alerts into a single incident: alerts on the same provider
 * within the correlation window join the open incident instead of creating noise.
 */
public interface IncidentCorrelationUseCase {

    long correlate(long organizationId, Long providerId, Severity severity, String alertReference, String title,
                   String description);

    String referenceOf(long organizationId, long incidentId);
}
