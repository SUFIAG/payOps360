package com.payops.reconciliation.adapter.config;

import com.payops.reconciliation.application.service.ReconciliationSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ReconciliationConfig.ReconciliationProperties.class)
class ReconciliationConfig {

    @Bean
    ReconciliationSettings reconciliationSettings(ReconciliationProperties p) {
        return new ReconciliationSettings(
                p.amountTolerance() == null ? new BigDecimal("0.01") : p.amountTolerance(),
                p.fuzzyWindow() == null ? Duration.ofDays(3) : p.fuzzyWindow(),
                p.missingAfter() == null ? Duration.ofDays(2) : p.missingAfter(),
                p.feeTolerance() == null ? new BigDecimal("0.05") : p.feeTolerance(),
                p.exceptionAlertThreshold() == null ? 5 : p.exceptionAlertThreshold(),
                p.maxLinesPerFile() == null ? 200_000 : p.maxLinesPerFile());
    }

    @ConfigurationProperties(prefix = "payops.reconciliation")
    record ReconciliationProperties(BigDecimal amountTolerance, Duration fuzzyWindow, Duration missingAfter,
                                    BigDecimal feeTolerance, Integer exceptionAlertThreshold, Integer maxLinesPerFile,
                                    Duration sweepInterval) {
    }
}
