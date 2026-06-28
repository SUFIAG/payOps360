package com.payops.payops360.alert.domain.model;

/**
 * Alert severity levels.
 */
public enum AlertSeverity {
    LOW("Low priority"),
    MEDIUM("Medium priority"),
    HIGH("High priority - requires attention"),
    CRITICAL("Critical - immediate attention required");

    private final String description;

    AlertSeverity(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if severity requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return this == HIGH || this == CRITICAL;
    }
}

