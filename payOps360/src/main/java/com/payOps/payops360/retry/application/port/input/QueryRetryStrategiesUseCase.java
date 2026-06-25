package com.payOps.payops360.retry.application.port.input;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import com.payOps.payops360.retry.domain.model.RetryStatus;

import java.util.UUID;

/**
 * Input Port: Query Retry Strategies Use Case
 */
public interface QueryRetryStrategiesUseCase {
    PagedResponse<RetryStrategy> findAll(RetryQueryParams params);

    record RetryQueryParams(
            UUID paymentId,
            RetryStatus status,
            String failureCategory,
            int page,
            int size
    ) {}
}

