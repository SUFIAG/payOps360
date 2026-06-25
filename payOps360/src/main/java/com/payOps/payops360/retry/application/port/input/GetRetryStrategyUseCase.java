package com.payOps.payops360.retry.application.port.input;

import com.payOps.payops360.retry.domain.model.RetryStrategy;

import java.util.UUID;

/**
 * Input Port: Get Retry Strategy Use Case
 */
public interface GetRetryStrategyUseCase {
    RetryStrategy getById(UUID retryId);
}

