package com.payOps.payops360.retry.domain.model;

/**
 * Retry Strategy Types
 */
public enum RetryStrategyType {
    IMMEDIATE("Retry immediately", "Quick retry without delay"),
    EXPONENTIAL_BACKOFF("Exponential backoff", "Retry with increasing delays"),
    FALLBACK_PROVIDER("Switch provider", "Route to alternative payment provider"),
    MANUAL_INTERVENTION("Manual review required", "Requires human intervention"),
    BLOCK_RETRY("Do not retry", "Payment should not be retried");

    private final String displayName;
    private final String description;

    RetryStrategyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

