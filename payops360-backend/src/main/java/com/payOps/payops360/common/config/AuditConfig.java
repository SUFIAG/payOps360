package com.payops.payops360.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing configuration.
 * Enables automatic tracking of created/updated timestamps and user information.
 */
@Configuration
@EnableJpaAuditing
public class AuditConfig {

    // Additional auditing configuration can be added here
    // For example: AuditorAware bean for tracking who made changes
}

