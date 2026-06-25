package com.payOps/payops360.failure.config;

import com.payOps.payops360.failure.domain.service.FailureClassificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Failure Classification Module
 */
@Configuration
public class FailureModuleConfig {

    @Bean
    public FailureClassificationService failureClassificationService() {
        return new FailureClassificationService();
    }
}

