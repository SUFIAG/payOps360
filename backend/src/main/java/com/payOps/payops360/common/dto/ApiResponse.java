package com.payops.payops360.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic API Response wrapper for all REST endpoints.
 * Provides consistent response structure across the application.
 *
 * @param <T> The type of data being returned
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Indicates if the request was successful
     */
    private boolean success;

    /**
     * The actual data payload (null if error)
     */
    private T data;

    /**
     * Error details (null if successful)
     */
    private ErrorDetail error;

    /**
     * Timestamp of the response
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Unique request identifier for tracking
     */
    private String requestId;

    /**
     * Create a successful response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a successful response with data and request ID
     */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .requestId(requestId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .timestamp(Instant.now())
                        .build())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response with request ID
     */
    public static <T> ApiResponse<T> error(String code, String message, String requestId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .timestamp(Instant.now())
                        .build())
                .requestId(requestId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response with full detail
     */
    public static <T> ApiResponse<T> error(ErrorDetail errorDetail, String requestId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(errorDetail)
                .requestId(requestId)
                .timestamp(Instant.now())
                .build();
    }
}

