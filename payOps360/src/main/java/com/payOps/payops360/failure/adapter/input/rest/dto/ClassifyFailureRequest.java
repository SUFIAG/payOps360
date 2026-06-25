package com.payOps/payops360.failure.adapter.input.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for failure classification
 */
public record ClassifyFailureRequest(
        @NotNull(message = "Payment ID is required")
        UUID paymentId,

        String errorCode,

        @NotBlank(message = "Error message is required")
        String errorMessage,

        String providerId
) {}

