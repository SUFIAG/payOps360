package com.payOps.payops360.failure.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Failure Classification Domain Model (Pure Domain - No Framework Dependencies)
 */
@Getter
@Builder
public class FailureClassification {
    private final UUID id;
    private final UUID paymentId;
    private final FailureType failureType;
    private final String errorCode;
    private final String errorMessage;
    private final String providerId;
    private final FailureSeverity severity;
    private final String category;
    private final boolean retryable;
    private final String recommendation;
    private final String reasoning;
    private final Instant classifiedAt;
    private final Map<String, Object> metadata;

    public boolean isCritical() {
        return severity == FailureSeverity.CRITICAL;
    }

    public boolean requiresImmediateAction() {
        return severity == FailureSeverity.CRITICAL || severity == FailureSeverity.HIGH;
    }

    public boolean isProviderRelated() {
        return failureType.isProviderIssue();
    }

    public boolean isCustomerRelated() {
        return failureType.isCustomerIssue();
    }
}

