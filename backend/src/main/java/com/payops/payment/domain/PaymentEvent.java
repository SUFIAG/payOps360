package com.payops.payment.domain;

import java.time.Instant;

/**
 * Immutable record of one lifecycle transition.
 */
public record PaymentEvent(PaymentStatus fromStatus, PaymentStatus toStatus, String reason, String errorCode,
                           String actor, Instant occurredAt) {
}
