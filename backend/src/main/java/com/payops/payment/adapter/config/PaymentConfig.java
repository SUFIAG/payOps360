package com.payops.payment.adapter.config;

import com.payops.payment.application.service.PaymentSettings;
import com.payops.payment.domain.PaymentStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(PaymentConfig.OperationsProperties.class)
class PaymentConfig {

    @Bean
    PaymentSettings paymentSettings(OperationsProperties properties) {
        Map<PaymentStatus, Duration> thresholds = new EnumMap<>(PaymentStatus.class);
        properties.stuckThresholds().forEach((status, duration) -> thresholds.put(PaymentStatus.valueOf(status), duration));
        return new PaymentSettings(thresholds);
    }

    @ConfigurationProperties(prefix = "payops.operations")
    record OperationsProperties(Map<String, Duration> stuckThresholds, Duration healthWindow, int retentionDays) {
    }
}
