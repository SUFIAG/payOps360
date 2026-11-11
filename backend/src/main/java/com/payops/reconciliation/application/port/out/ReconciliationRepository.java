package com.payops.reconciliation.application.port.out;

import com.payops.reconciliation.application.port.in.BatchView;
import com.payops.reconciliation.application.port.in.ExceptionView;
import com.payops.reconciliation.application.port.in.ReconciliationQueryUseCase;
import com.payops.reconciliation.domain.ExceptionStatus;
import com.payops.reconciliation.domain.ExceptionType;
import com.payops.reconciliation.domain.FeeSchedule;
import com.payops.reconciliation.domain.MappingProfile;
import com.payops.reconciliation.domain.MatchMethod;
import com.payops.reconciliation.domain.MatchStatus;
import com.payops.reconciliation.domain.ResolutionReason;
import com.payops.reconciliation.domain.SettlementLineType;
import com.payops.reconciliation.domain.SettlementRecord;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence for settlement batches, lines, exceptions, fee schedules and
 * column mappings.
 */
public interface ReconciliationRepository {

    // ------------------------------------------------------------ batches

    long createBatch(long organizationId, long providerId, String reference, String fileName, String provisionalChecksum,
                     String uploadedBy, Instant now);

    void insertLines(long organizationId, long batchId, List<SettlementRecord> records);

    boolean checksumExists(long organizationId, String checksum);

    /** Stores the final checksum and parse statistics; throws ConflictException on a duplicate checksum. */
    void finishImport(long batchId, String checksum, int totalLines, int rejectedLines, List<String> parseErrors);

    Optional<BatchKey> batchKey(long organizationId, String reference);

    Optional<BatchKey> batchKey(long batchId);

    Optional<BatchView> batch(long organizationId, long batchId);

    PageResult<BatchView> batches(long organizationId, Long providerId, PageQuery page);

    /** Atomically moves a RECEIVED (or stale MATCHING) batch to MATCHING. */
    boolean claimBatch(long batchId, Instant now, Instant staleBefore);

    void updateProgress(long batchId, int processedLines);

    /** Recomputes the batch counters from its lines and exceptions and marks it COMPLETED. */
    void completeBatch(long batchId, Instant now);

    void failBatch(long batchId, String message, Instant now);

    List<BatchKey> stalledBatches(Instant receivedBefore, Instant matchingStartedBefore);

    // ------------------------------------------------------------ lines

    List<LineRecord> pendingLines(long batchId, long afterId, int limit);

    void updateLines(List<LineUpdate> updates);

    /** "paymentId:TYPE" keys of payments already matched (or mismatched) by a settlement line. */
    Set<String> settledPaymentKeys(Collection<Long> paymentIds);

    Optional<LineRecord> line(long lineId);

    // ------------------------------------------------------------ exceptions

    /** Inserts exceptions, skipping ones that already exist (e.g. a payment already flagged missing). */
    int insertExceptions(List<NewException> exceptions);

    Optional<ExceptionState> exceptionState(long organizationId, String reference);

    Optional<ExceptionView> exception(long organizationId, long exceptionId);

    PageResult<ExceptionView> exceptions(long organizationId, ExceptionQuery query, PageQuery page);

    /** Optimistic update of the workflow fields; false when the version changed concurrently. */
    boolean updateException(ExceptionState state, Instant now);

    /** Resolves open MISSING_IN_SETTLEMENT exceptions of payments that have now been settled. */
    int resolveMissingInSettlement(long organizationId, Collection<Long> paymentIds, String note, Instant now);

    ReconciliationQueryUseCase.Summary summary(long organizationId, int days, Instant since);

    List<ReconciliationQueryUseCase.TrendPoint> trend(long organizationId, Instant since);

    // ------------------------------------------------------------ configuration

    Optional<StoredFeeSchedule> feeSchedule(long organizationId, long providerId);

    void replaceFeeSchedule(long organizationId, long providerId, List<FeeSchedule.Rule> rules, String actor, Instant now);

    Optional<StoredMapping> mapping(long organizationId, long providerId);

    void saveMapping(long organizationId, long providerId, MappingProfile profile, String actor, Instant now);

    // ------------------------------------------------------------ records

    record BatchKey(long id, long organizationId, long providerId, String reference) {
    }

    record LineRecord(long id, long batchId, int lineNo, SettlementLineType type, String providerReference,
                      BigDecimal amount, String currency, BigDecimal fee, BigDecimal expectedFee, BigDecimal fxRate,
                      Instant settledAt, MatchStatus matchStatus, MatchMethod matchMethod, Long paymentId) {
    }

    record LineUpdate(long lineId, MatchStatus status, MatchMethod method, Long paymentId, BigDecimal expectedFee) {
    }

    record NewException(String reference, long organizationId, long providerId, Long batchId, Long lineId,
                        Long paymentId, ExceptionType type, BigDecimal amountAtRisk, String currency,
                        BigDecimal expectedAmount, BigDecimal actualAmount, String paymentReference,
                        String providerReference, String detail, Instant createdAt) {
    }

    record ExceptionState(long id, long organizationId, long providerId, Long batchId, Long lineId, Long paymentId,
                          ExceptionType type, ExceptionStatus status, String assignee,
                          ResolutionReason resolutionReason, String resolutionNote, String resolvedBy,
                          Instant resolvedAt, long version) {

        public ExceptionState with(ExceptionStatus newStatus, String newAssignee, ResolutionReason reason,
                                   String note, String by, Instant at) {
            return new ExceptionState(id, organizationId, providerId, batchId, lineId, paymentId, type, newStatus,
                    newAssignee, reason, note, by, at, version);
        }
    }

    record ExceptionQuery(Set<ExceptionType> types, Set<ExceptionStatus> statuses, Long providerId, Long batchId,
                          String assignee) {
    }

    record StoredFeeSchedule(List<FeeSchedule.Rule> rules, String updatedBy, Instant updatedAt) {
    }

    record StoredMapping(MappingProfile profile, String updatedBy, Instant updatedAt) {
    }
}
