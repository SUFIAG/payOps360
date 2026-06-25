package com.payops.payops360.payment.domain.config;

import com.payops.payops360.payment.domain.service.PaymentLifecycleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for domain services.
 *
 * Domain services are pure business logic classes with no framework dependencies.
 * We create them as Spring beans here for dependency injection.
 */
@Configuration
public class PaymentDomainConfig {

    @Bean
    public PaymentLifecycleService paymentLifecycleService() {
        return new PaymentLifecycleService();
    }
}

