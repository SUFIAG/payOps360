package com.payOps.payops360.retry.domain.model;

/**
 * Retry Execution Status
 */
public enum RetryStatus {
    RECOMMENDED,    // Strategy recommended, not yet executed
    EXECUTING,      // Retry is in progress
    SUCCESS,        // Retry succeeded
    FAILED,         // Retry failed
    EXHAUSTED,      // Max attempts reached
    BLOCKED         // Retry is blocked
}

