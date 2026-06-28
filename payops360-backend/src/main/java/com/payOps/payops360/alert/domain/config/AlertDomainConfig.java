package com.payops.payops360.alert.domain.config;

import com.payops.payops360.alert.domain.service.AlertDetectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for alert domain services.
 */
@Configuration
public class AlertDomainConfig {
    
    @Bean
    public AlertDetectionService alertDetectionService() {
        return new AlertDetectionService();
    }
}
