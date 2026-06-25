package com.payOps.payops360.failure.domain.service;

import com.payOps.payops360.failure.domain.model.FailureClassification;
import com.payOps.payops360.failure.domain.model.FailureSeverity;
import com.payOps.payops360.failure.domain.model.FailureType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain Service for Failure Classification Logic (Pure Domain Logic)
 */
public class FailureClassificationService {

    /**
     * Classify a payment failure based on error details
     */
    public FailureClassification classifyFailure(
            UUID paymentId,
            String errorCode,
            String errorMessage,
            String providerId
    ) {
        FailureType failureType = determineFailureType(errorCode, errorMessage);
        FailureSeverity severity = determineSeverity(failureType);
        String category = categorizeFailure(failureType);
        boolean retryable = failureType.isRetryable();
        String recommendation = generateRecommendation(failureType, retryable);
        String reasoning = generateReasoning(failureType, errorCode, errorMessage);

        return FailureClassification.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .failureType(failureType)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .providerId(providerId)
                .severity(severity)
                .category(category)
                .retryable(retryable)
                .recommendation(recommendation)
                .reasoning(reasoning)
                .classifiedAt(Instant.now())
                .metadata(buildMetadata(failureType))
                .build();
    }

    private FailureType determineFailureType(String errorCode, String errorMessage) {
        if (errorCode == null && errorMessage == null) {
            return FailureType.UNKNOWN;
        }

        String combined = (errorCode + " " + errorMessage).toLowerCase();

        // Network & Infrastructure
        if (combined.contains("timeout") || combined.contains("timed out")) {
            return FailureType.TIMEOUT;
        }
        if (combined.contains("network") || combined.contains("connection")) {
            return FailureType.NETWORK_FAILURE;
        }
 if (combined.contains("dns")) {
            return FailureType.DNS_FAILURE;
        }
        if (combined.contains("ssl") || combined.contains("tls") || combined.contains("certificate")) {
            return FailureType.SSL_ERROR;
        }

        // Provider Issues
        if (combined.contains("provider unavailable") || combined.contains("service unavailable")) {
            return FailureType.PROVIDER_UNAVAILABLE;
        }
        if (combined.contains("rate limit") || combined.contains("too many requests")) {
            return FailureType.PROVIDER_RATE_LIMIT;
        }
        if (combined.contains("provider error") || combined.contains("gateway error")) {
            return FailureType.PROVIDER_ERROR;
        }
        if (combined.contains("provider timeout")) {
            return FailureType.PROVIDER_TIMEOUT;
        }

        // Validation & Business Rules
        if (combined.contains("validation") || combined.contains("invalid data")) {
            return FailureType.VALIDATION_ERROR;
        }
        if (combined.contains("duplicate") || combined.contains("already processed")) {
            return FailureType.DUPLICATE_TRANSACTION;
        }
        if (combined.contains("business rule") || combined.contains("not allowed")) {
            return FailureType.BUSINESS_RULE_VIOLATION;
        }

        // Payment Method Issues
        if (combined.contains("insufficient funds") || combined.contains("insufficient balance")) {
            return FailureType.INSUFFICIENT_FUNDS;
        }
        if (combined.contains("invalid card") || combined.contains("card invalid")) {
            return FailureType.INVALID_CARD;
        }
        if (combined.contains("expired") || combined.contains("card expired")) {
            return FailureType.EXPIRED_CARD;
        }
        if (combined.contains("declined") || combined.contains("card declined")) {
            return FailureType.CARD_DECLINED;
        }
        if (combined.contains("blocked") || combined.contains("card blocked")) {
            return FailureType.CARD_BLOCKED;
        }

        // Security & Fraud
        if (combined.contains("fraud") || combined.contains("suspicious")) {
            return FailureType.FRAUD_SUSPECTED;
        }
        if (combined.contains("3d secure") || combined.contains("3ds")) {
            return FailureType.THREE_D_SECURE_FAILED;
        }
        if (combined.contains("security") || combined.contains("authentication failed")) {
            return FailureType.SECURITY_VIOLATION;
        }

        // Configuration
        if (combined.contains("configuration") || combined.contains("misconfigured")) {
            return FailureType.INVALID_CONFIGURATION;
        }
        if (combined.contains("credentials") || combined.contains("unauthorized")) {
            return FailureType.MISSING_CREDENTIALS;
        }

        return FailureType.UNKNOWN;
    }

    private FailureSeverity determineSeverity(FailureType failureType) {
        return switch (failureType) {
            case PROVIDER_UNAVAILABLE, PROVIDER_ERROR, FRAUD_SUSPECTED, SECURITY_VIOLATION,
                 INVALID_CONFIGURATION, MISSING_CREDENTIALS -> FailureSeverity.CRITICAL;

            case PROVIDER_TIMEOUT, TIMEOUT, NETWORK_FAILURE, PROVIDER_RATE_LIMIT,
                 THREE_D_SECURE_FAILED -> FailureSeverity.HIGH;

            case VALIDATION_ERROR, BUSINESS_RULE_VIOLATION, DUPLICATE_TRANSACTION,
                 CARD_DECLINED, CARD_BLOCKED -> FailureSeverity.MEDIUM;

            case INSUFFICIENT_FUNDS, INVALID_CARD, EXPIRED_CARD, DNS_FAILURE,
                 SSL_ERROR, UNKNOWN -> FailureSeverity.LOW;
        };
    }

    private String categorizeFailure(FailureType failureType) {
        if (failureType.isProviderIssue()) {
            return "PROVIDER_ISSUE";
        }
        if (failureType.isCustomerIssue()) {
            return "CUSTOMER_ISSUE";
        }
        return switch (failureType) {
            case NETWORK_FAILURE, TIMEOUT, DNS_FAILURE, SSL_ERROR -> "INFRASTRUCTURE_ISSUE";
            case FRAUD_SUSPECTED, SECURITY_VIOLATION, THREE_D_SECURE_FAILED -> "SECURITY_ISSUE";
            case VALIDATION_ERROR, BUSINESS_RULE_VIOLATION, DUPLICATE_TRANSACTION -> "VALIDATION_ISSUE";
            case INVALID_CONFIGURATION, MISSING_CREDENTIALS -> "CONFIGURATION_ISSUE";
            default -> "UNKNOWN_ISSUE";
        };
    }

    private String generateRecommendation(FailureType failureType, boolean retryable) {
        if (!retryable) {
            return switch (failureType) {
                case INSUFFICIENT_FUNDS -> "Contact customer to update payment method or add funds";
                case INVALID_CARD, EXPIRED_CARD -> "Request customer to provide valid card details";
                case CARD_DECLINED -> "Advise customer to contact their bank";
                case CARD_BLOCKED -> "Customer should contact card issuer to unblock card";
                case FRAUD_SUSPECTED -> "Manual review required - potential fraud";
                case DUPLICATE_TRANSACTION -> "Check for duplicate submission, do not retry";
                case BUSINESS_RULE_VIOLATION -> "Review business rules and customer eligibility";
                case INVALID_CONFIGURATION, MISSING_CREDENTIALS -> "Fix system configuration immediately";
                default -> "Manual investigation required";
            };
        }

        return switch (failureType) {
            case NETWORK_FAILURE, TIMEOUT -> "Retry immediately - transient network issue";
            case PROVIDER_TIMEOUT, PROVIDER_UNAVAILABLE -> "Retry with exponential backoff or switch provider";
            case PROVIDER_RATE_LIMIT -> "Wait and retry after rate limit window";
            case DNS_FAILURE -> "Retry after DNS resolution check";
            default -> "Consider retry with caution";
        };
    }

    private String generateReasoning(FailureType failureType, String errorCode, String errorMessage) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("Failure classified as ").append(failureType.getDescription()).append(". ");

        if (errorCode != null) {
            reasoning.append("Error code: ").append(errorCode).append(". ");
        }

        reasoning.append(switch (failureType) {
            case NETWORK_FAILURE, TIMEOUT -> "Network issues are typically transient and resolve quickly.";
            case PROVIDER_UNAVAILABLE, PROVIDER_ERROR -> "Provider experiencing systemic issues. May require provider switch.";
            case PROVIDER_TIMEOUT -> "Provider not responding within acceptable time. Consider fallback.";
            case PROVIDER_RATE_LIMIT -> "Rate limit protection triggered. Implement backoff strategy.";
            case INSUFFICIENT_FUNDS -> "Customer payment method lacks sufficient funds.";
            case FRAUD_SUSPECTED -> "Transaction flagged by fraud detection system.";
            case DUPLICATE_TRANSACTION -> "Duplicate transaction detected. Idempotency check failed.";
            case VALIDATION_ERROR -> "Data validation failed. Check request format and values.";
            case INVALID_CONFIGURATION -> "System misconfiguration detected. Requires immediate fix.";
            default -> "Requires investigation to determine root cause and resolution.";
        });

        return reasoning.toString();
    }

    private Map<String, Object> buildMetadata(FailureType failureType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("is_retryable", failureType.isRetryable());
        metadata.put("is_provider_issue", failureType.isProviderIssue());
        metadata.put("is_customer_issue", failureType.isCustomerIssue());
        metadata.put("failure_type", failureType.name());
        return metadata;
    }
}

