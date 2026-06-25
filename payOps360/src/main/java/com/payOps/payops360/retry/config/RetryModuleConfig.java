package com.payOps.payops360.retry.config;

import com.payOps.payops360.retry.domain.service.RetryStrategyRecommendationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Retry Module
 */
@Configuration
public class RetryModuleConfig {

    @Bean
    public RetryStrategyRecommendationService retryStrategyRecommendationService() {
        return new RetryStrategyRecommendationService();
    }
}

