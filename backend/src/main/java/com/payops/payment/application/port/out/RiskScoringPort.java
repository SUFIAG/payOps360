package com.payops.payment.application.port.out;

import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.RiskAssessment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time risk scoring of a payment at ingestion time.
 */
public interface RiskScoringPort {

    RiskAssessment assess(RiskInput input);

    record RiskInput(long organizationId, String customerRef, BigDecimal amount, String currency,
                     PaymentMethod method, String country, long providerId, Instant at) {
    }
}
