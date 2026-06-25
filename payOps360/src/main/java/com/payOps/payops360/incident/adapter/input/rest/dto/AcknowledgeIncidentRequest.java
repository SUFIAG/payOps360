package com.payOps/payops360.incident.adapter.input.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for acknowledging incident
 */
public record AcknowledgeIncidentRequest(
        @NotBlank(message = "Assigned to is required")
        String assignedTo
) {}

