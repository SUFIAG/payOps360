package com.payops.payops360.payment.domain.model;

import com.payops.payops360.common.exception.IllegalStateTransitionException;
import com.payops.payops360.payment.domain.valueobject.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Payment Domain Model - Core business entity.
 *
 * This is a pure domain model with NO framework dependencies.
 * It contains ALL business logic related to payments.
 *
 * Hexagonal Architecture:
 * - This belongs to the DOMAIN layer
 * - No Spring, no JPA annotations
 * - Pure business logic only
 * - Rich domain model (not anemic)
 */
@Getter
@Builder(toBuilder = true)
public class Payment {

    // Identity
    private final Long id;
    private final String paymentId;  // Business identifier
    private final String externalTransactionId;
    private final String merchantReference;

    // Financial Information
    private final Money amount;

    // Provider Information
    private final String providerId;
    private final String providerName;
    private final String providerTransactionId;

    // Customer Information
    private final String customerId;
    private final PaymentMethodType paymentMethodType;
    private final String paymentMethodLast4;

    // Status & Lifecycle
    private PaymentStatus status;
    private PaymentStatus previousStatus;
    private Instant statusChangedAt;

    // Tracking
    @Builder.Default
    private int retryCount = 0;
    private boolean isStuck;
    private Instant stuckDetectedAt;

    // Metadata
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
    @Builder.Default
    private List<String> tags = List.of();

    // Timeline
    @Builder.Default
    private final List<PaymentEvent> events = new ArrayList<>();

    // Timestamps
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Transition payment to a new status with validation
     *
     * @param newStatus The target status
     * @param reason Reason for the transition
     * @throws IllegalStateTransitionException if transition is not allowed
     */
    public void transitionTo(PaymentStatus newStatus, String reason) {
        if (this.status == newStatus) {
            return; // Already in the target state
        }

        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateTransitionException(
                this.status.name(),
                newStatus.name(),
                "This transition is not allowed by business rules"
            );
        }

        // Record the event
        PaymentEvent event = PaymentEvent.builder()
                .fromStatus(this.status)
                .toStatus(newStatus)
                .reason(reason)
                .occurredAt(Instant.now())
                .build();

        this.events.add(event);
        this.previousStatus = this.status;
        this.status = newStatus;
        this.statusChangedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark payment as failed
     */
    public void markAsFailed(String reason) {
        transitionTo(PaymentStatus.FAILED, reason);
    }

    /**
     * Initiate retry
     */
    public void initiateRetry() {
        if (this.status != PaymentStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed payments");
        }

        transitionTo(PaymentStatus.RETRY_PENDING, "Retry initiated");
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Start retry execution
     */
    public void startRetryExecution() {
        if (this.status != PaymentStatus.RETRY_PENDING) {
            throw new IllegalStateException("Payment must be in RETRY_PENDING status");
        }

        transitionTo(PaymentStatus.RETRY_IN_PROGRESS, "Retry execution started");
    }

    /**
     * Mark payment as stuck (e.g., in same state for too long)
     */
    public void markAsStuck() {
        if (!this.isStuck) {
            this.isStuck = true;
            this.stuckDetectedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Clear stuck status (payment progressed)
     */
    public void clearStuckStatus() {
        if (this.isStuck) {
            this.isStuck = false;
            this.stuckDetectedAt = null;
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Check if payment is in a terminal state
     */
    public boolean isTerminal() {
        return this.status.isTerminal();
    }

    /**
     * Check if payment has failed
     */
    public boolean isFailed() {
        return this.status.isFailure();
    }

    /**
     * Check if payment is successful
     */
    public boolean isSuccess() {
        return this.status.isSuccess();
    }

    /**
     * Check if payment can be retried
     */
    public boolean canBeRetried() {
        return this.status == PaymentStatus.FAILED && this.retryCount < 3;
    }

    /**
     * Get duration in current status (in seconds)
     */
    public long getDurationInCurrentStatusSeconds() {
        if (statusChangedAt == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - statusChangedAt.getEpochSecond();
    }

    /**
     * Check if payment is stuck based on time in current status
     */
    public boolean isStuckBasedOnDuration() {
        long seconds = getDurationInCurrentStatusSeconds();

        return switch (this.status) {
            case PROCESSING -> seconds > 300; // 5 minutes
            case AUTHORIZED -> seconds > 600; // 10 minutes
            case RETRY_PENDING -> seconds > 1800; // 30 minutes
            default -> false;
        };
    }

    /**
     * Get immutable copy of events
     */
    public List<PaymentEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * Get payment timeline (ordered events)
     */
    public List<PaymentEvent> getTimeline() {
        return events.stream()
                .sorted((e1, e2) -> e1.getOccurredAt().compareTo(e2.getOccurredAt()))
                .toList();
    }
}

