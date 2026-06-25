package com.payOps.payops360.analytics.config;

import com.payOps.payops360.analytics.domain.service.AnalyticsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Analytics Module
 */
@Configuration
public class AnalyticsModuleConfig {

    @Bean
    public AnalyticsService analyticsService() {
        return new AnalyticsService();
    }
}

