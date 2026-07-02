package com.payops.payops360.provider.domain.config;

import com.payops.payops360.provider.domain.service.ProviderHealthCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for provider domain services.
 */
@Configuration
public class ProviderDomainConfig {

    @Bean
    public ProviderHealthCalculator providerHealthCalculator() {
        return new ProviderHealthCalculator();
    }
}

