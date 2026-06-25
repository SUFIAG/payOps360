package com.payOps.payops360.retry.application.port.input;

import com.payOps.payops360.retry.domain.model.RetryStrategy;

import java.util.UUID;

/**
 * Input Port: Recommend Retry Strategy Use Case
 */
public interface RecommendRetryStrategyUseCase {
    RetryStrategy recommend(RecommendRetryCommand command);

    record RecommendRetryCommand(
            UUID paymentId,
            String failureCategory,
            Double providerSuccessRate,
            Integer failureCount
    ) {}
}

