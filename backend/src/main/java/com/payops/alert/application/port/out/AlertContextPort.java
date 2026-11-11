package com.payops.alert.application.port.out;

import com.payops.shared.domain.Severity;

import java.util.Optional;

/**
 * Outbound collaborations of the alert module: provider naming and
 * incident correlation.
 */
public interface AlertContextPort {

    Optional<String> providerCode(long organizationId, long providerId);

    Optional<String> incidentReference(long organizationId, long incidentId);

    /**
     * Opens or reuses an incident for a significant alert and returns its id.
     */
    Optional<Long> correlateIncident(long organizationId, Long providerId, Severity severity, String alertReference,
                                     String title, String description);
}
