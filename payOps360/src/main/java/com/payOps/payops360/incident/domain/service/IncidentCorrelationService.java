package com.payOps.payops360.incident.domain.service;

import com.payOps.payops360.incident.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service for Incident Correlation Logic (Pure Domain Logic)
 * Analyzes alerts and failures to create and correlate incidents
 */
public class IncidentCorrelationService {

    /**
     * Create incident from correlated alerts
     */
    public Incident createIncident(
            List<AlertData> alerts,
            String providerId,
            String region
    ) {
        IncidentCategory category = determineCategory(alerts);
        IncidentSeverity severity = calculateSeverity(alerts);

        String title = generateTitle(category, providerId, alerts.size());
        String description = generateDescription(alerts, category);

        int impactedPaymentCount = alerts.stream()
                .mapToInt(a -> a.relatedPaymentCount())
                .sum();

        double estimatedImpact = calculateImpact(impactedPaymentCount, severity);

        List<UUID> alertIds = alerts.stream().map(AlertData::alertId).toList();
        List<UUID> paymentIds = alerts.stream()
                .flatMap(a -> a.relatedPaymentIds().stream())
                .distinct()
                .toList();

        return Incident.builder()
                .id(UUID.randomUUID())
                .title(title)
                .description(description)
                .severity(severity)
                .status(IncidentStatus.OPEN)
                .category(category)
                .affectedProviderId(providerId)
                .affectedRegion(region)
                .impactedPaymentCount(impactedPaymentCount)
                .estimatedImpact(estimatedImpact)
                .detectedAt(Instant.now())
                .relatedAlertIds(alertIds)
                .relatedPaymentIds(paymentIds)
                .build();
    }

    /**
     * Check if alerts should be correlated into single incident
     */
    public boolean shouldCorrelate(AlertData alert1, AlertData alert2) {
        // Same provider AND similar time window (within 10 minutes)
        boolean sameProvider = alert1.providerId() != null
                && alert1.providerId().equals(alert2.providerId());

        boolean withinTimeWindow = Math.abs(
                alert1.triggeredAt().toEpochMilli() - alert2.triggeredAt().toEpochMilli()
        ) < 600_000; // 10 minutes

        // Similar alert types (both failures, both timeouts, etc.)
        boolean similarType = alert1.alertType().contains("FAILURE")
                && alert2.alertType().contains("FAILURE");

        return (sameProvider && withinTimeWindow) || similarType;
    }

    private IncidentCategory determineCategory(List<AlertData> alerts) {
        // Check predominant alert patterns
        long providerFailures = alerts.stream()
                .filter(a -> a.alertType().contains("PROVIDER"))
                .count();

        long networkIssues = alerts.stream()
                .filter(a -> a.alertType().contains("NETWORK") || a.alertType().contains("TIMEOUT"))
                .count();

        long fraudAlerts = alerts.stream()
                .filter(a -> a.alertType().contains("FRAUD"))
                .count();

        if (providerFailures > alerts.size() / 2) {
            return IncidentCategory.PROVIDER_OUTAGE;
        }

        if (networkIssues > alerts.size() / 2) {
            return IncidentCategory.NETWORK_ISSUE;
        }

        if (fraudAlerts > 0) {
            return IncidentCategory.FRAUD_SPIKE;
        }

        return IncidentCategory.HIGH_FAILURE_RATE;
    }

    private IncidentSeverity calculateSeverity(List<AlertData> alerts) {
        int criticalCount = (int) alerts.stream()
                .filter(a -> "CRITICAL".equals(a.severity()))
                .count();

        int highCount = (int) alerts.stream()
                .filter(a -> "HIGH".equals(a.severity()))
                .count();

        int totalPayments = alerts.stream()
                .mapToInt(AlertData::relatedPaymentCount)
                .sum();

        // Critical if multiple critical alerts or massive payment impact
        if (criticalCount >= 2 || totalPayments > 1000) {
            return IncidentSeverity.CRITICAL;
        }

        // High if critical alert or many high alerts
        if (criticalCount > 0 || highCount >= 3 || totalPayments > 500) {
            return IncidentSeverity.HIGH;
        }

        // Medium if multiple alerts
        if (alerts.size() >= 3 || totalPayments > 100) {
            return IncidentSeverity.MEDIUM;
        }

        return IncidentSeverity.LOW;
    }

    private String generateTitle(IncidentCategory category, String providerId, int alertCount) {
        String providerName = providerId != null ? providerId : "System";
        return switch (category) {
            case PROVIDER_OUTAGE -> String.format("%s Provider Outage (%d alerts)", providerName, alertCount);
            case NETWORK_ISSUE -> String.format("Network Issues affecting %s (%d alerts)", providerName, alertCount);
            case PERFORMANCE_DEGRADATION -> String.format("Performance Degradation - %s (%d alerts)", providerName, alertCount);
            case HIGH_FAILURE_RATE -> String.format("High Failure Rate Detected (%d alerts)", alertCount);
            case FRAUD_SPIKE -> String.format("Fraud Activity Spike (%d alerts)", alertCount);
            case SYSTEM_ERROR -> String.format("System Errors - %s (%d alerts)", providerName, alertCount);
            case UNKNOWN -> String.format("Unknown Incident (%d alerts)", alertCount);
        };
    }

    private String generateDescription(List<AlertData> alerts, IncidentCategory category) {
        StringBuilder desc = new StringBuilder();
        desc.append("Correlated incident from ").append(alerts.size()).append(" related alerts. ");
        desc.append("Category: ").append(category).append(". ");

        String firstAlertType = alerts.get(0).alertType();
        desc.append("Primary alert type: ").append(firstAlertType).append(".");

        return desc.toString();
    }

    private double calculateImpact(int paymentCount, IncidentSeverity severity) {
        // Simplified impact score: payment count * severity multiplier
        double severityMultiplier = switch (severity) {
            case LOW -> 1.0;
            case MEDIUM -> 2.0;
            case HIGH -> 5.0;
            case CRITICAL -> 10.0;
        };

        return paymentCount * severityMultiplier;
    }

    /**
     * Data class for alert information (used by domain service)
     */
    public record AlertData(
            UUID alertId,
            String alertType,
            String severity,
            String providerId,
            Instant triggeredAt,
            int relatedPaymentCount,
            List<UUID> relatedPaymentIds
    ) {}
}

