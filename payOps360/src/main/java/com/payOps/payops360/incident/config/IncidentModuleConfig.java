package com.payOps.payops360.incident.config;

import com.payOps.payops360.incident.domain.service.IncidentCorrelationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Incident Module
 */
@Configuration
public class IncidentModuleConfig {

    @Bean
    public IncidentCorrelationService incidentCorrelationService() {
        return new IncidentCorrelationService();
    }
}

