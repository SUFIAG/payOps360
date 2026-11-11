package com.payops.incident.application.port.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface IncidentContextPort {

    Optional<String> providerCode(long organizationId, long providerId);

    Optional<Long> providerId(long organizationId, String providerCode);

    /** Failed or stuck payments since {@code since}, optionally limited to one provider. */
    Impact impactSince(long organizationId, Long providerId, Instant since);

    record Impact(int payments, BigDecimal amount) {
    }
}
