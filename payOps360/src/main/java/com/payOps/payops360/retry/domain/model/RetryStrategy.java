package com.payOps.payops360.retry.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Retry Strategy Domain Model (Pure Domain - No Framework Dependencies)
 * Represents an intelligent retry recommendation for failed payments
 */
@Getter
@Builder
public class RetryStrategy {
    private final UUID id;
    private final UUID paymentId;
    private final String failureCategory;
    private final RetryStrategyType strategyType;
    private final RetryStatus status;
    private final int maxAttempts;
    private final long delayMillis;
    private final String fallbackProviderId;
    private final String recommendation;
    private final String reasoning;
    private final Instant recommendedAt;
    private final Instant executedAt;
    private final Integer currentAttempt;
    private final boolean successful;

    public RetryStrategy markAsExecuted() {
        return RetryStrategy.builder()
                .id(this.id)
                .paymentId(this.paymentId)
                .failureCategory(this.failureCategory)
                .strategyType(this.strategyType)
                .status(RetryStatus.EXECUTING)
                .maxAttempts(this.maxAttempts)
                .delayMillis(this.delayMillis)
                .fallbackProviderId(this.fallbackProviderId)
                .recommendation(this.recommendation)
                .reasoning(this.reasoning)
                .recommendedAt(this.recommendedAt)
                .executedAt(Instant.now())
                .currentAttempt(this.currentAttempt != null ? this.currentAttempt + 1 : 1)
                .successful(this.successful)
                .build();
    }

    public RetryStrategy markAsSuccess() {
        return RetryStrategy.builder()
                .id(this.id)
                .paymentId(this.paymentId)
                .failureCategory(this.failureCategory)
                .strategyType(this.strategyType)
                .status(RetryStatus.SUCCESS)
                .maxAttempts(this.maxAttempts)
                .delayMillis(this.delayMillis)
                .fallbackProviderId(this.fallbackProviderId)
                .recommendation(this.recommendation)
                .reasoning(this.reasoning)
                .recommendedAt(this.recommendedAt)
                .executedAt(this.executedAt)
                .currentAttempt(this.currentAttempt)
                .successful(true)
                .build();
    }

    public RetryStrategy markAsFailed() {
        return RetryStrategy.builder()
                .id(this.id)
                .paymentId(this.paymentId)
                .failureCategory(this.failureCategory)
                .strategyType(this.strategyType)
                .status(RetryStatus.FAILED)
                .maxAttempts(this.maxAttempts)
                .delayMillis(this.delayMillis)
                .fallbackProviderId(this.fallbackProviderId)
                .recommendation(this.recommendation)
                .reasoning(this.reasoning)
                .recommendedAt(this.recommendedAt)
                .executedAt(this.executedAt)
                .currentAttempt(this.currentAttempt)
                .successful(false)
                .build();
    }

    public boolean canRetry() {
        return this.status == RetryStatus.RECOMMENDED
            && (this.currentAttempt == null || this.currentAttempt < this.maxAttempts);
    }

    public boolean isRetryExhausted() {
        return this.currentAttempt != null && this.currentAttempt >= this.maxAttempts;
    }
}

