package com.payops.payops360.alert.domain.model;

/**
 * Types of alerts in the system.
 */
public enum AlertType {
    // Provider Alerts
    PROVIDER_DOWN("Provider is not responding"),
    PROVIDER_DEGRADED("Provider experiencing performance issues"),
    HIGH_LATENCY("Provider latency exceeds threshold"),
    HIGH_TIMEOUT_RATE("Provider timeout rate is high"),
    SLA_BREACH("Provider SLA has been breached"),

    // Payment Alerts
    HIGH_FAILURE_RATE("Payment failure rate is high"),
    STUCK_PAYMENTS("Multiple payments stuck in same state"),
    RETRY_EXHAUSTED("Multiple payments exhausted retry attempts"),

    // Pattern Alerts
    UNUSUAL_SPIKE("Unusual spike in metric detected"),
    UNUSUAL_DIP("Unusual dip in metric detected"),

    // System Alerts
    SYSTEM_ERROR("System error detected"),
    THRESHOLD_BREACH("Metric threshold breached");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this is a provider-related alert
     */
    public boolean isProviderAlert() {
        return name().startsWith("PROVIDER_") ||
               this == HIGH_LATENCY ||
               this == HIGH_TIMEOUT_RATE ||
               this == SLA_BREACH;
    }

    /**
     * Check if this is a payment-related alert
     */
    public boolean isPaymentAlert() {
        return this == HIGH_FAILURE_RATE ||
               this == STUCK_PAYMENTS ||
               this == RETRY_EXHAUSTED;
    }
}

