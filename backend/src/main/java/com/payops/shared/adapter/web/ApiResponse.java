package com.payops.shared.adapter.web;

import java.time.Instant;

/**
 * Uniform response envelope for every REST endpoint.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
