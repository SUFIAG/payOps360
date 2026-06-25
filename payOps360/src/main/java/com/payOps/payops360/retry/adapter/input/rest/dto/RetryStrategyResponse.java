package com.payOps.payops360.retry.adapter.input.rest.dto;

import com.payOps.payops360.retry.domain.model.RetryStatus;
import com.payOps.payops360.retry.domain.model.RetryStrategyType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for retry strategy
 */
public record RetryStrategyResponse(
        UUID id,
        UUID paymentId,
        String failureCategory,
        RetryStrategyType strategyType,
        RetryStatus status,
        int maxAttempts,
        long delayMillis,
        String fallbackProviderId,
        String recommendation,
        String reasoning,
        Instant recommendedAt,
        Instant executedAt,
        Integer currentAttempt,
        boolean successful
) {}

