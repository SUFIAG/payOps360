package com.payOps.payops360.retry.application.service;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.common.exception.ResourceNotFoundException;
import com.payOps.payops360.retry.application.port.input.*;
import com.payOps.payops360.retry.application.port.output.RetryStrategyRepository;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import com.payOps.payops360.retry.domain.service.RetryStrategyRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application Service implementing all Retry Strategy Use Cases
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RetryStrategyService implements
        RecommendRetryStrategyUseCase,
        ExecuteRetryUseCase,
        GetRetryStrategyUseCase,
        QueryRetryStrategiesUseCase {

    private final RetryStrategyRepository retryStrategyRepository;
    private final RetryStrategyRecommendationService recommendationService;

    @Override
    public RetryStrategy recommend(RecommendRetryCommand command) {
        log.info("Recommending retry strategy for payment: {}", command.paymentId());

        RetryStrategy strategy = recommendationService.recommendStrategy(
                command.paymentId(),
                command.failureCategory(),
                command.providerSuccessRate(),
                command.failureCount()
        );

        RetryStrategy saved = retryStrategyRepository.save(strategy);
        log.info("Retry strategy recommended: {} - {}", saved.getStrategyType(), saved.getRecommendation());

        return saved;
    }

    @Override
    public RetryStrategy execute(UUID retryId) {
        log.info("Executing retry strategy: {}", retryId);

        RetryStrategy strategy = retryStrategyRepository.findById(retryId)
                .orElseThrow(() -> new ResourceNotFoundException("Retry strategy not found: " + retryId));

        if (!strategy.canRetry()) {
            log.warn("Cannot execute retry - either already exhausted or in wrong status: {}", strategy.getStatus());
            throw new IllegalStateException("Cannot execute retry in status: " + strategy.getStatus());
        }

        RetryStrategy executing = strategy.markAsExecuted();
        RetryStrategy updated = retryStrategyRepository.save(executing);

        log.info("Retry strategy execution started: {} (attempt {}/{})",
                retryId, updated.getCurrentAttempt(), updated.getMaxAttempts());

        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public RetryStrategy getById(UUID retryId) {
        return retryStrategyRepository.findById(retryId)
                .orElseThrow(() -> new ResourceNotFoundException("Retry strategy not found: " + retryId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RetryStrategy> findAll(RetryQueryParams params) {
        return retryStrategyRepository.findAll(params);
    }
}

