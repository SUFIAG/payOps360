package com.payops.reconciliation.application.port.input;

import com.payops.reconciliation.domain.ExceptionStatus;
import com.payops.reconciliation.domain.ExceptionType;
import com.payops.reconciliation.domain.ResolutionReason;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One reconciliation exception in the work queue.
 */
public record ExceptionView(String reference, ExceptionType type, ExceptionStatus status, String providerCode,
                            String providerName, String batchReference, String paymentReference,
                            String providerReference, BigDecimal amountAtRisk, String currency,
                            BigDecimal amountAtRiskUsd, BigDecimal expectedAmount, BigDecimal actualAmount,
                            String detail, String assignee, ResolutionReason resolutionReason, String resolutionNote,
                            String resolvedBy, Instant resolvedAt, Instant createdAt, Instant updatedAt,
                            long ageHours) {
}
