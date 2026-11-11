package com.payops.reconciliation.application.port.in;

import com.payops.reconciliation.domain.ExceptionStatus;
import com.payops.reconciliation.domain.ExceptionType;
import com.payops.reconciliation.domain.MatchMethod;
import com.payops.reconciliation.domain.MatchStatus;
import com.payops.reconciliation.domain.SettlementLineType;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Read side of reconciliation: batches, the exception queue and KPIs.
 * Every query is scoped to one organization.
 */
public interface ReconciliationQueryUseCase {

    PageResult<BatchView> batches(long organizationId, String providerCode, PageQuery page);

    BatchView batch(long organizationId, String reference);

    PageResult<ExceptionView> exceptions(long organizationId, ExceptionFilter filter, PageQuery page);

    ExceptionDetails exception(long organizationId, String reference);

    Summary summary(long organizationId, int days);

    java.util.List<TrendPoint> matchRateTrend(long organizationId, int days);

    record ExceptionFilter(Set<ExceptionType> types, Set<ExceptionStatus> statuses, String providerCode,
                           String batchReference, String assignee) {
    }

    /** Exception plus the settlement line and ledger payment it concerns (either may be absent). */
    record ExceptionDetails(ExceptionView exception, LineView line, PaymentSnapshot payment) {
    }

    record LineView(int lineNo, SettlementLineType type, String providerReference, BigDecimal amount, String currency,
                    BigDecimal fee, BigDecimal expectedFee, BigDecimal fxRate, Instant settledAt,
                    MatchStatus matchStatus, MatchMethod matchMethod) {
    }

    record PaymentSnapshot(String reference, String merchantReference, BigDecimal amount, String currency,
                           String method, String status, String providerCode, Instant createdAt) {
    }

    /**
     * Headline KPIs over the last {@code days}. Amounts are converted to USD.
     *
     * @param matchRate percentage of reconcilable (non-fee) lines that matched, null when nothing was imported
     */
    record Summary(int days, BigDecimal matchRate, long reconciledLines, long matchedLines, long batches,
                   long openExceptions, BigDecimal amountAtRiskUsd, BigDecimal feeLeakageUsd,
                   Map<ExceptionType, Long> openByType) {
    }

    record TrendPoint(LocalDate day, long lines, long matched, BigDecimal matchRate) {
    }
}
