package com.payOps.payops360.retry.adapter.input.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for retry recommendation
 */
public record RetryRecommendationRequest(
        @NotNull(message = "Payment ID is required")
        UUID paymentId,

        @NotBlank(message = "Failure category is required")
        String failureCategory,

        Double providerSuccessRate,

        @NotNull(message = "Failure count is required")
        @Min(value = 0, message = "Failure count cannot be negative")
        Integer failureCount
) {}

