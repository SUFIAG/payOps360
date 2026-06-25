package com.payOps/payops360.failure.adapter.input.rest.dto;

import com.payOps/payops360.failure.domain.model.FailureSeverity;
import com.payOps/payops360.failure.domain.model.FailureType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for failure classification
 */
public record FailureClassificationResponse(
        UUID id,
        UUID paymentId,
        FailureType failureType,
        String errorCode,
        String errorMessage,
        String providerId,
        FailureSeverity severity,
        String category,
        boolean retryable,
        String recommendation,
        String reasoning,
        Instant classifiedAt,
        Map<String, Object> metadata
) {}

