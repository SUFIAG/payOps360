package com.payOps.payops360.retry.domain.service;

import com.payOps.payops360.retry.domain.model.RetryStatus;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import com.payOps.payops360.retry.domain.model.RetryStrategyType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Service for Retry Strategy Recommendation Logic (Pure Domain Logic)
 */
public class RetryStrategyRecommendationService {

    /**
     * Recommend retry strategy based on failure category and provider health
     */
    public RetryStrategy recommendStrategy(
            UUID paymentId,
            String failureCategory,
            Double providerSuccessRate,
            Integer failureCount
    ) {
        RetryStrategyType strategyType = determineStrategyType(failureCategory, providerSuccessRate, failureCount);

        return RetryStrategy.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .failureCategory(failureCategory)
                .strategyType(strategyType)
                .status(RetryStatus.RECOMMENDED)
                .maxAttempts(calculateMaxAttempts(strategyType, failureCount))
                .delayMillis(calculateDelay(strategyType, failureCount))
                .fallbackProviderId(determineFallbackProvider(strategyType))
                .recommendation(generateRecommendation(strategyType, failureCategory))
                .reasoning(generateReasoning(strategyType, failureCategory, providerSuccessRate, failureCount))
                .recommendedAt(Instant.now())
                .currentAttempt(0)
                .successful(false)
                .build();
    }

    private RetryStrategyType determineStrategyType(String failureCategory, Double providerSuccessRate, Integer failureCount) {
        // BLOCK_RETRY conditions
        if ("FRAUD_BLOCK".equals(failureCategory) || "BUSINESS_RULE_BLOCK".equals(failureCategory)) {
            return RetryStrategyType.BLOCK_RETRY;
        }

        if ("INSUFFICIENT_FUNDS".equals(failureCategory)) {
            return RetryStrategyType.MANUAL_INTERVENTION;
        }

        // IMMEDIATE retry conditions
        if (failureCount == 0 && ("NETWORK_FAILURE".equals(failureCategory) || "TIMEOUT".equals(failureCategory))) {
            return RetryStrategyType.IMMEDIATE;
        }

        // FALLBACK_PROVIDER conditions
        if (providerSuccessRate != null && providerSuccessRate < 0.5) {
            return RetryStrategyType.FALLBACK_PROVIDER;
        }

        if ("PROVIDER_FAILURE".equals(failureCategory) && failureCount > 1) {
            return RetryStrategyType.FALLBACK_PROVIDER;
        }

        // EXPONENTIAL_BACKOFF (default intelligent retry)
        if (failureCount > 0 && failureCount < 3) {
            return RetryStrategyType.EXPONENTIAL_BACKOFF;
        }

        // Too many failures - manual intervention
        if (failureCount >= 3) {
            return RetryStrategyType.MANUAL_INTERVENTION;
        }

        return RetryStrategyType.EXPONENTIAL_BACKOFF;
    }

    private int calculateMaxAttempts(RetryStrategyType strategyType, Integer failureCount) {
        return switch (strategyType) {
            case IMMEDIATE -> 1;
            case EXPONENTIAL_BACKOFF -> Math.max(3 - failureCount, 1);
            case FALLBACK_PROVIDER -> 2;
            case MANUAL_INTERVENTION, BLOCK_RETRY -> 0;
        };
    }

    private long calculateDelay(RetryStrategyType strategyType, Integer failureCount) {
        return switch (strategyType) {
            case IMMEDIATE -> 0L;
            case EXPONENTIAL_BACKOFF -> (long) Math.pow(2, failureCount) * 1000; // 1s, 2s, 4s, 8s...
            case FALLBACK_PROVIDER -> 5000L; // 5 seconds
            case MANUAL_INTERVENTION, BLOCK_RETRY -> 0L;
        };
    }

    private String determineFallbackProvider(RetryStrategyType strategyType) {
        if (strategyType == RetryStrategyType.FALLBACK_PROVIDER) {
            return "FALLBACK_PROVIDER"; // In real system, this would be determined by provider registry
        }
        return null;
    }

    private String generateRecommendation(RetryStrategyType strategyType, String failureCategory) {
        return switch (strategyType) {
            case IMMEDIATE -> "Retry payment immediately - transient failure detected";
            case EXPONENTIAL_BACKOFF -> "Retry with exponential backoff to avoid overwhelming system";
            case FALLBACK_PROVIDER -> "Switch to fallback provider due to recurring failures";
            case MANUAL_INTERVENTION -> "Manual review required - " + failureCategory;
            case BLOCK_RETRY -> "Do not retry - " + failureCategory;
        };
    }

    private String generateReasoning(RetryStrategyType strategyType, String failureCategory,
                                     Double providerSuccessRate, Integer failureCount) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("Failure Category: ").append(failureCategory).append(". ");

        if (providerSuccessRate != null) {
            reasoning.append("Provider Success Rate: ").append(String.format("%.2f%%", providerSuccessRate * 100)).append(". ");
        }

        reasoning.append("Failure Count: ").append(failureCount).append(". ");

        reasoning.append(switch (strategyType) {
            case IMMEDIATE -> "Network/timeout failures are typically transient and succeed on immediate retry.";
            case EXPONENTIAL_BACKOFF -> "Exponential backoff prevents system overload and allows temporary issues to resolve.";
            case FALLBACK_PROVIDER -> "Provider experiencing systemic issues. Fallback provider recommended.";
            case MANUAL_INTERVENTION -> "Business logic or funds issue requires human review.";
            case BLOCK_RETRY -> "Security or business rules prevent automatic retry.";
        });

        return reasoning.toString();
    }
}

