package com.payops.reconciliation.application.port.input;

import com.payops.reconciliation.domain.BatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A settlement file import and its matching progress / outcome.
 *
 * @param matchRate percentage of non-fee lines that matched the ledger (null until lines are processed)
 */
public record BatchView(String reference, String providerCode, String providerName, String fileName, BatchStatus status,
                        int totalLines, int rejectedLines, int processedLines, int progressPercent, int matched,
                        int amountMismatches, int missingInLedger, int missingInSettlement, int duplicates,
                        int feeOvercharges, int exceptions, BigDecimal matchRate, BigDecimal grossAmountUsd,
                        BigDecimal feeAmountUsd, Instant periodStart, Instant periodEnd, List<String> parseErrors,
                        String errorMessage, String uploadedBy, Instant createdAt, Instant startedAt,
                        Instant completedAt) {
}
