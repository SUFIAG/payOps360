package com.payops.payops360.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Detailed error information for API responses.
 * Provides structured error data for client consumption.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    /**
     * Error code for programmatic handling
     */
    private String code;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Additional error details or field-specific validation errors
     */
    private Map<String, Object> details;

    /**
     * Timestamp when the error occurred
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Stack trace or debug information (only in dev mode)
     */
    private String debugInfo;
}

