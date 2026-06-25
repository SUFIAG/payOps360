package com.payops.payops360.failure.domain.model;

/**
 * Comprehensive failure type classification.
 */
public enum FailureType {
    // Network & Infrastructure
    NETWORK_FAILURE("Network connection issue"),
    TIMEOUT("Request timeout"),
    DNS_FAILURE("DNS resolution failed"),
    SSL_ERROR("SSL/TLS error"),

    // Provider Issues
    PROVIDER_ERROR("Provider system error"),
    PROVIDER_TIMEOUT("Provider not responding"),
    PROVIDER_UNAVAILABLE("Provider unavailable"),
    PROVIDER_RATE_LIMIT("Provider rate limit exceeded"),

    // Validation & Business Rules
    VALIDATION_ERROR("Invalid data format"),
    BUSINESS_RULE_VIOLATION("Business rule violation"),
    DUPLICATE_TRANSACTION("Duplicate transaction"),

    // Payment Method Issues
    INSUFFICIENT_FUNDS("Insufficient funds"),
    INVALID_CARD("Invalid card details"),
    EXPIRED_CARD("Card expired"),
    CARD_DECLINED("Card declined by issuer"),
    CARD_BLOCKED("Card blocked"),

    // Security & Fraud
    FRAUD_SUSPECTED("Fraud suspected"),
    SECURITY_VIOLATION("Security check failed"),
    THREE_D_SECURE_FAILED("3D Secure authentication failed"),

    // Configuration Issues
    INVALID_CONFIGURATION("Invalid configuration"),
    MISSING_CREDENTIALS("Missing credentials"),

    // Unknown
    UNKNOWN("Unknown error");

    private final String description;

    FailureType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if failure is retryable
     */
    public boolean isRetryable() {
        return switch (this) {
            case NETWORK_FAILURE, TIMEOUT, PROVIDER_TIMEOUT, PROVIDER_UNAVAILABLE,
                 PROVIDER_RATE_LIMIT, DNS_FAILURE -> true;
            default -> false;
        };
    }

    /**
     * Check if failure is a provider issue
     */
    public boolean isProviderIssue() {
        return name().startsWith("PROVIDER_");
    }

    /**
     * Check if failure is a customer issue
     */
    public boolean isCustomerIssue() {
        return this == INSUFFICIENT_FUNDS || this == INVALID_CARD ||
               this == EXPIRED_CARD || this == CARD_DECLINED || this == CARD_BLOCKED;
    }
}

