package com.payOps.payops360.retry.application.port.output;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.retry.application.port.input.QueryRetryStrategiesUseCase;
import com.payOps.payops360.retry.domain.model.RetryStrategy;

import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: Retry Strategy Repository
 */
public interface RetryStrategyRepository {
    RetryStrategy save(RetryStrategy retryStrategy);
    Optional<RetryStrategy> findById(UUID id);
    PagedResponse<RetryStrategy> findAll(QueryRetryStrategiesUseCase.RetryQueryParams params);
}

