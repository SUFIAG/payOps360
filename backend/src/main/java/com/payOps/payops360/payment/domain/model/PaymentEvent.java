package com.payops.payops360.payment.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Payment Event - represents a state transition or significant occurrence.
 *
 * This is a value object within the Payment aggregate.
 * Pure domain model with no framework dependencies.
 */
@Getter
@Builder
public class PaymentEvent {

    private final PaymentStatus fromStatus;
    private final PaymentStatus toStatus;
    private final String reason;
    private final String errorCode;
    private final String errorMessage;
    private final Long durationMs;
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();
    private final Instant occurredAt;

    /**
     * Check if this event represents a state change
     */
    public boolean isStateChange() {
        return fromStatus != null && toStatus != null && !fromStatus.equals(toStatus);
    }

    /**
     * Check if this event represents an error
     */
    public boolean isError() {
        return errorCode != null || errorMessage != null;
    }

    /**
     * Check if transition was to a failure state
     */
    public boolean isFailureTransition() {
        return toStatus != null && toStatus.isFailure();
    }

    /**
     * Check if transition was to a success state
     */
    public boolean isSuccessTransition() {
        return toStatus != null && toStatus.isSuccess();
    }
}

