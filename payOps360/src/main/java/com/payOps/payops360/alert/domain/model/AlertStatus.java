package com.payops.payops360.alert.domain.model;

/**
 * Alert lifecycle status.
 */
public enum AlertStatus {
    OPEN("Alert is open and requires attention"),
    ACKNOWLEDGED("Alert has been acknowledged by a user"),
    INVESTIGATING("Alert is being investigated"),
    RESOLVED("Alert has been resolved"),
    AUTO_RESOLVED("Alert auto-resolved (condition cleared)"),
    SUPPRESSED("Alert has been suppressed");

    private final String description;

    AlertStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if alert is in a terminal state
     */
    public boolean isTerminal() {
        return this == RESOLVED || this == AUTO_RESOLVED || this == SUPPRESSED;
    }

    /**
     * Check if alert is active (needs attention)
     */
    public boolean isActive() {
        return !isTerminal();
    }
}

