package com.payops.payops360.alert.domain.service;

import com.payops.payops360.alert.domain.model.Alert;
import com.payops.payops360.alert.domain.model.AlertSeverity;
import com.payops.payops360.alert.domain.model.AlertType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Alert Detection Service - Domain Service
 *
 * Pure business logic for detecting and creating alerts.
 * No framework dependencies.
 */
public class AlertDetectionService {

    /**
     * Detect provider down alert
     */
    public Alert detectProviderDown(String providerId, String providerName) {
        return createAlert(
                AlertType.PROVIDER_DOWN,
                AlertSeverity.CRITICAL,
                "PROVIDER",
                providerId,
                String.format("Provider '%s' is not responding", providerName),
                String.format("Provider %s appears to be down. No successful responses in the last 5 minutes.", providerName),
                null,
                null,
                null
        );
    }

    /**
     * Detect high latency alert
     */
    public Alert detectHighLatency(String providerId, long actualLatency, long threshold) {
        return createAlert(
                AlertType.HIGH_LATENCY,
                AlertSeverity.HIGH,
                "PROVIDER",
                providerId,
                String.format("High latency detected: %dms (threshold: %dms)", actualLatency, threshold),
                String.format("Provider is experiencing high latency. P95 latency is %dms, exceeding SLA threshold of %dms.",
                        actualLatency, threshold),
                "p95_latency",
                (double) actualLatency,
                (double) threshold
        );
    }

    /**
     * Detect provider degraded alert
     */
    public Alert detectProviderDegraded(String providerId, double successRate, double threshold) {
        return createAlert(
                AlertType.PROVIDER_DEGRADED,
                AlertSeverity.HIGH,
                "PROVIDER",
                providerId,
                String.format("Provider degraded: %.2f%% success rate", successRate),
                String.format("Provider success rate is %.2f%%, below threshold of %.2f%%.", successRate, threshold),
                "success_rate",
                successRate,
                threshold
        );
    }

    /**
     * Detect high failure rate alert
     */
    public Alert detectHighFailureRate(String providerId, double failureRate, double threshold) {
        return createAlert(
                AlertType.HIGH_FAILURE_RATE,
                AlertSeverity.HIGH,
                "PROVIDER",
                providerId,
                String.format("High failure rate: %.2f%%", failureRate),
                String.format("Payment failure rate is %.2f%%, exceeding threshold of %.2f%%.", failureRate, threshold),
                "failure_rate",
                failureRate,
                threshold
        );
    }

    /**
     * Detect stuck payments alert
     */
    public Alert detectStuckPayments(int count, String status) {
        return createAlert(
                AlertType.STUCK_PAYMENTS,
                count > 20 ? AlertSeverity.CRITICAL : AlertSeverity.HIGH,
                "PAYMENT",
                "MULTIPLE",
                String.format("%d payments stuck in %s state", count, status),
                String.format("Detected %d payments stuck in %s state for more than expected duration.", count, status),
                "stuck_count",
                (double) count,
                10.0
        );
    }

    /**
     * Detect SLA breach alert
     */
    public Alert detectSLABreach(String providerId, String metricName, double actual, double sla) {
        return createAlert(
                AlertType.SLA_BREACH,
                AlertSeverity.HIGH,
                "PROVIDER",
                providerId,
                String.format("SLA breach: %s", metricName),
                String.format("Provider SLA breached. %s is %.2f, SLA requires %.2f.", metricName, actual, sla),
                metricName,
                actual,
                sla
        );
    }

    /**
     * Create alert with all parameters
     */
    private Alert createAlert(
            AlertType type,
            AlertSeverity severity,
            String entityType,
            String entityId,
            String title,
            String description,
            String metricName,
            Double metricValue,
            Double thresholdValue) {

        Instant now = Instant.now();

        return Alert.builder()
                .alertId(generateAlertId())
                .alertType(type)
                .severity(severity)
                .entityType(entityType)
                .entityId(entityId)
                .title(title)
                .description(description)
                .metricName(metricName)
                .metricValue(metricValue)
                .thresholdValue(thresholdValue)
                .status(com.payops.payops360.alert.domain.model.AlertStatus.OPEN)
                .detectedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusChangedAt(now)
                .build();
    }

    /**
     * Generate unique alert ID
     */
    private String generateAlertId() {
        return "ALT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * Check if alert should be auto-resolved
     */
    public boolean shouldAutoResolve(Alert alert, boolean conditionCleared) {
        return alert.isActive() && conditionCleared;
    }
}

