package com.payOps.payops360.retry.application.port.input;

import com.payOps.payops360.retry.domain.model.RetryStrategy;

import java.util.UUID;

/**
 * Input Port: Execute Retry Use Case
 */
public interface ExecuteRetryUseCase {
    RetryStrategy execute(UUID retryId);
}

