package com.payOps.payops360.incident.adapter.input.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for resolving incident
 */
public record ResolveIncidentRequest(
        @NotBlank(message = "Root cause is required")
        String rootCause,

        @NotBlank(message = "Resolution is required")
        String resolution
) {}

