package com.payOps/payops360.ai.config;

import com.payOps/payops360.ai.domain.service.AIInvestigationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AI Module
 */
@Configuration
public class AIModuleConfig {

    @Bean
    public AIInvestigationService aiInvestigationService() {
        return new AIInvestigationService();
    }
}

