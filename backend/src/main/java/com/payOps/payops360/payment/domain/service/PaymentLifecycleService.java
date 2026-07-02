package com.payops.payops360.payment.domain.service;

import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain service for payment lifecycle validation and management.
 *
 * Pure domain logic - no framework dependencies.
 * This service coordinates business rules across payment entities.
 */
@Slf4j
public class PaymentLifecycleService {

    /**
     * Validate if payment state transition is allowed
     */
    public boolean canTransition(Payment payment, PaymentStatus targetStatus) {
        if (payment == null || targetStatus == null) {
            return false;
        }

        return payment.getStatus().canTransitionTo(targetStatus);
    }

    /**
     * Check if payment should be marked as stuck
     */
    public boolean shouldBeMarkedAsStuck(Payment payment) {
        if (payment.isTerminal()) {
            return false; // Terminal states are fine
        }

        return payment.isStuckBasedOnDuration();
    }

    /**
     * Calculate payment health score (0-100)
     * Higher is better
     */
    public int calculateHealthScore(Payment payment) {
        int score = 100;

        // Deduct points for failures
        if (payment.isFailed()) {
            score -= 50;
        }

        // Deduct points for retries
        score -= (payment.getRetryCount() * 10);

        // Deduct points if stuck
        if (payment.isStuck()) {
            score -= 30;
        }

        // Deduct points for time in non-terminal state
        if (!payment.isTerminal()) {
            long durationSeconds = payment.getDurationInCurrentStatusSeconds();
            if (durationSeconds > 600) { // More than 10 minutes
                score -= 20;
            } else if (durationSeconds > 300) { // More than 5 minutes
                score -= 10;
            }
        }

        return Math.max(0, score);
    }

    /**
     * Determine if payment needs attention
     */
    public boolean needsAttention(Payment payment) {
        return payment.isStuck() ||
               payment.isFailed() ||
               (payment.getRetryCount() > 2) ||
               (!payment.isTerminal() && payment.getDurationInCurrentStatusSeconds() > 900);
    }

    /**
     * Get recommended action for a payment
     */
    public String getRecommendedAction(Payment payment) {
        if (payment.isTerminal() && payment.isSuccess()) {
            return "NONE - Payment completed successfully";
        }

        if (payment.isTerminal() && payment.isFailed()) {
            return "MANUAL_REVIEW - Payment permanently failed";
        }

        if (payment.isStuck()) {
            return "INVESTIGATE - Payment stuck in " + payment.getStatus();
        }

        if (payment.isFailed() && payment.canBeRetried()) {
            return "RETRY - Automatic retry recommended";
        }

        if (payment.isFailed() && !payment.canBeRetried()) {
            return "ESCALATE - Retry limit exceeded";
        }

        if (payment.getDurationInCurrentStatusSeconds() > 600) {
            return "MONITOR - Payment taking longer than expected";
        }

        return "NONE - Payment progressing normally";
    }
}

