package com.payops.payops360.payment.domain.model;

/**
 * Payment lifecycle states following a strict state machine.
 *
 * State Transitions (allowed paths):
 * INITIATED -> AUTHORIZED, FAILED, CANCELLED
 * AUTHORIZED -> CAPTURED, FAILED, CANCELLED
 * CAPTURED -> PROCESSING, FAILED
 * PROCESSING -> SETTLED, FAILED
 * SETTLED -> REFUNDED, CHARGEBACK
 * FAILED -> RETRY_PENDING
 * RETRY_PENDING -> RETRY_IN_PROGRESS
 * RETRY_IN_PROGRESS -> SETTLED, RETRY_FAILED
 */
public enum PaymentStatus {

    // Initial states
    INITIATED("Payment initiated"),
    AUTHORIZED("Payment authorized"),
    CAPTURED("Payment captured"),

    // Processing states
    PROCESSING("Payment being processed"),
    SETTLED("Payment settled successfully"),

    // Failure states
    FAILED("Payment failed"),
    RETRY_PENDING("Retry pending"),
    RETRY_IN_PROGRESS("Retry in progress"),
    RETRY_FAILED("Retry failed"),

    // Final states
    REFUNDED("Payment refunded"),
    CHARGEBACK("Payment charged back"),
    CANCELLED("Payment cancelled");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if transition to another status is allowed
     */
    public boolean canTransitionTo(PaymentStatus newStatus) {
        return switch (this) {
            case INITIATED -> newStatus == AUTHORIZED || newStatus == FAILED || newStatus == CANCELLED;
            case AUTHORIZED -> newStatus == CAPTURED || newStatus == FAILED || newStatus == CANCELLED;
            case CAPTURED -> newStatus == PROCESSING || newStatus == FAILED;
            case PROCESSING -> newStatus == SETTLED || newStatus == FAILED;
            case SETTLED -> newStatus == REFUNDED || newStatus == CHARGEBACK;
            case FAILED -> newStatus == RETRY_PENDING;
            case RETRY_PENDING -> newStatus == RETRY_IN_PROGRESS;
            case RETRY_IN_PROGRESS -> newStatus == SETTLED || newStatus == RETRY_FAILED;
            default -> false; // Terminal states cannot transition
        };
    }

    /**
     * Check if this is a terminal state (no further transitions possible)
     */
    public boolean isTerminal() {
        return this == SETTLED || this == RETRY_FAILED ||
               this == REFUNDED || this == CHARGEBACK || this == CANCELLED;
    }

    /**
     * Check if this is a failure state
     */
    public boolean isFailure() {
        return this == FAILED || this == RETRY_FAILED;
    }

    /**
     * Check if this is a success state
     */
    public boolean isSuccess() {
        return this == SETTLED;
    }
}

