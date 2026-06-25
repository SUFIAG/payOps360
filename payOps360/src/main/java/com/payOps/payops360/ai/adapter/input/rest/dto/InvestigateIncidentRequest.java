package com.payOps/payops360.ai.adapter.input.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for triggering AI investigation
 */
public record InvestigateIncidentRequest(
        @NotNull(message = "Incident ID is required")
        UUID incidentId,

        String model  // "gpt-4", "claude-3", "mock" (defaults to "mock")
) {
    public InvestigateIncidentRequest {
        if (model == null || model.isBlank()) {
            model = "mock";  // Default to mock for testing
        }
    }
}

