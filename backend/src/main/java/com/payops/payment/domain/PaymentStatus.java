package com.payops.payment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment lifecycle state machine.
 *
 * <pre>
 * INITIATED ──► AUTHORIZED ──► CAPTURED ──► PROCESSING ──► SETTLED ──► REFUNDED | CHARGEBACK
 *     │              │             │             │
 *     ▼              ▼             ▼             ▼
 *  CANCELLED      FAILED ◄──────────────────────┘
 *                   │
 *                   ▼
 *             RETRY_PENDING ──► RETRY_IN_PROGRESS ──► AUTHORIZED | SETTLED | RETRY_FAILED
 * </pre>
 */
public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    CAPTURED,
    PROCESSING,
    SETTLED,
    FAILED,
    RETRY_PENDING,
    RETRY_IN_PROGRESS,
    RETRY_FAILED,
    REFUNDED,
    CHARGEBACK,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(INITIATED, EnumSet.of(AUTHORIZED, FAILED, CANCELLED)),
            Map.entry(AUTHORIZED, EnumSet.of(CAPTURED, FAILED, CANCELLED)),
            Map.entry(CAPTURED, EnumSet.of(PROCESSING, SETTLED, FAILED)),
            Map.entry(PROCESSING, EnumSet.of(SETTLED, FAILED)),
            Map.entry(SETTLED, EnumSet.of(REFUNDED, CHARGEBACK)),
            Map.entry(FAILED, EnumSet.of(RETRY_PENDING)),
            Map.entry(RETRY_PENDING, EnumSet.of(RETRY_IN_PROGRESS, CANCELLED)),
            Map.entry(RETRY_IN_PROGRESS, EnumSet.of(AUTHORIZED, SETTLED, RETRY_FAILED)),
            Map.entry(RETRY_FAILED, EnumSet.noneOf(PaymentStatus.class)),
            Map.entry(REFUNDED, EnumSet.noneOf(PaymentStatus.class)),
            Map.entry(CHARGEBACK, EnumSet.noneOf(PaymentStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(PaymentStatus.class)));

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public Set<PaymentStatus> allowedTransitions() {
        Set<PaymentStatus> allowed = TRANSITIONS.get(this);
        return allowed.isEmpty() ? EnumSet.noneOf(PaymentStatus.class) : EnumSet.copyOf(allowed);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public boolean isFailure() {
        return this == FAILED || this == RETRY_FAILED;
    }

    /** The provider accepted the payment (authorisation or later). */
    public boolean isAccepted() {
        return this == AUTHORIZED || this == CAPTURED || this == PROCESSING || this == SETTLED
                || this == REFUNDED || this == CHARGEBACK;
    }

    public boolean isInFlight() {
        return this == INITIATED || this == AUTHORIZED || this == CAPTURED || this == PROCESSING
                || this == RETRY_PENDING || this == RETRY_IN_PROGRESS;
    }
}
