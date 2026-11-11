package com.payops.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Filter for payment search. Every field is optional.
 */
public record PaymentSearchCriteria(
        Set<PaymentStatus> statuses,
        Long providerId,
        PaymentMethod method,
        RiskAssessment.RiskLevel minRiskLevel,
        String currency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String customerRef,
        String text,
        String country,
        Boolean stuck,
        Instant from,
        Instant to) {

    public static PaymentSearchCriteria empty() {
        return new PaymentSearchCriteria(Set.of(), null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
