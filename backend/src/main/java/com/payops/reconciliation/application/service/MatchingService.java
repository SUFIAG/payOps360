package com.payops.reconciliation.application.service;

import com.payops.reconciliation.application.port.in.BatchView;
import com.payops.reconciliation.application.port.in.ReconciliationProcessingUseCase;
import com.payops.reconciliation.application.port.out.LedgerPort;
import com.payops.reconciliation.application.port.out.MatchingQueue;
import com.payops.reconciliation.application.port.out.ReconciliationRepository;
import com.payops.reconciliation.application.port.out.ReconciliationRepository.LineRecord;
import com.payops.reconciliation.application.port.out.ReconciliationRepository.LineUpdate;
import com.payops.reconciliation.application.port.out.ReconciliationRepository.NewException;
import com.payops.reconciliation.domain.ExceptionType;
import com.payops.reconciliation.domain.FeeSchedule;
import com.payops.reconciliation.domain.MatchMethod;
import com.payops.reconciliation.domain.MatchStatus;
import com.payops.reconciliation.domain.MatchingRules;
import com.payops.reconciliation.domain.SettlementLineType;
import com.payops.shared.application.OperationalEvent;
import com.payops.shared.application.OperationalEventPublisher;
import com.payops.shared.domain.References;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The matching engine. Lines are processed in chunks, each in its own
 * transaction, so progress is visible while a batch runs and a crashed run
 * resumes where it stopped.
 *
 * <ol>
 *   <li>Exact: the line reference equals a payment's PayOps, merchant or provider reference.</li>
 *   <li>Fuzzy (SALE only): same provider and currency, amount within tolerance, created within the
 *       window before settlement, not yet settled. The closest candidate wins.</li>
 *   <li>Outcome: MATCHED, AMOUNT_MISMATCH, DUPLICATE (payment already settled by another line of the
 *       same type) or MISSING_IN_LEDGER. Matched SALE lines are also checked against the fee schedule.</li>
 *   <li>Afterwards, succeeded payments inside the batch's coverage window that no line settled are
 *       flagged MISSING_IN_SETTLEMENT.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService implements ReconciliationProcessingUseCase {

    private static final int CHUNK = 500;
    private static final int MISSING_BATCH_LIMIT = 1_000;
    private static final Duration STALE_RUN = Duration.ofMinutes(15);
    private static final Duration QUEUE_GRACE = Duration.ofMinutes(1);

    private final ReconciliationRepository repository;
    private final LedgerPort ledger;
    private final MatchingQueue matchingQueue;
    private final SettlementImportService feeSchedules;
    private final ReconciliationSettings settings;
    private final OperationalEventPublisher operationalEvents;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Override
    public void processBatch(long organizationId, long batchId) {
        Instant now = clock.instant();
        ReconciliationRepository.BatchKey batch = repository.batchKey(batchId).orElse(null);
        if (batch == null || batch.organizationId() != organizationId || !repository.claimBatch(batchId, now, now.minus(STALE_RUN))) {
            return;
        }
        try {
            LedgerPort.ProviderRef provider = ledger.providerById(organizationId, batch.providerId())
                    .orElseThrow(() -> new IllegalStateException("provider not found"));
            FeeSchedule fees = feeSchedules.feeScheduleFor(organizationId, provider);
            long afterId = 0;
            while (true) {
                List<LineRecord> lines = repository.pendingLines(batchId, afterId, CHUNK);
                if (lines.isEmpty()) {
                    break;
                }
                transactions.executeWithoutResult(status -> matchChunk(batch, fees, lines));
                afterId = lines.get(lines.size() - 1).id();
                repository.updateProgress(batchId, lines.size());
            }
            transactions.executeWithoutResult(status -> flagMissingInBatchWindow(batch));
            repository.completeBatch(batchId, clock.instant());
            repository.batch(organizationId, batchId).ifPresent(view -> notifyIfNeeded(organizationId, view));
            log.info("Settlement batch {} reconciled", batch.reference());
        } catch (RuntimeException e) {
            log.error("Matching failed for settlement batch {}", batch.reference(), e);
            repository.failBatch(batchId, "Matching failed: " + e.getClass().getSimpleName(), clock.instant());
        }
    }

    private void matchChunk(ReconciliationRepository.BatchKey batch, FeeSchedule fees, List<LineRecord> lines) {
        long orgId = batch.organizationId();
        Instant now = clock.instant();
        Set<String> references = new HashSet<>();
        lines.stream().map(LineRecord::providerReference).filter(Objects::nonNull).forEach(references::add);
        Map<String, LedgerPort.LedgerPayment> byReference = new HashMap<>();
        for (LedgerPort.LedgerPayment p : references.isEmpty() ? List.<LedgerPort.LedgerPayment>of()
                : ledger.findByReferences(orgId, references)) {
            byReference.putIfAbsent(p.reference(), p);
            if (p.merchantReference() != null) {
                byReference.putIfAbsent(p.merchantReference(), p);
            }
            if (p.providerReference() != null) {
                byReference.putIfAbsent(p.providerReference(), p);
            }
        }
        Set<String> settled = new HashSet<>(repository.settledPaymentKeys(
                byReference.values().stream().map(LedgerPort.LedgerPayment::id).distinct().toList()));

        List<LineUpdate> updates = new ArrayList<>(lines.size());
        List<NewException> exceptions = new ArrayList<>();
        Set<Long> settledPayments = new HashSet<>();
        BigDecimal tolerance = settings.amountTolerance();

        for (LineRecord line : lines) {
            if (line.type() == SettlementLineType.FEE) {
                updates.add(new LineUpdate(line.id(), MatchStatus.FEE_ONLY, null, null, null));
                continue;
            }
            LedgerPort.LedgerPayment payment = line.providerReference() == null ? null : byReference.get(line.providerReference());
            MatchMethod method = MatchMethod.EXACT;
            if (payment == null && line.type() == SettlementLineType.SALE) {
                payment = fuzzyMatch(batch, line, settled);
                method = MatchMethod.FUZZY;
            }
            if (payment == null) {
                updates.add(new LineUpdate(line.id(), MatchStatus.MISSING_IN_LEDGER, null, null, null));
                exceptions.add(exception(batch, line, null, ExceptionType.MISSING_IN_LEDGER, line.amount().abs(),
                        line.currency(), null, line.amount(),
                        "No ledger payment matches settlement reference " + line.providerReference(), now));
                continue;
            }
            String key = payment.id() + ":" + line.type().name();
            if (!settled.add(key)) {
                updates.add(new LineUpdate(line.id(), MatchStatus.DUPLICATE, method, payment.id(), null));
                exceptions.add(exception(batch, line, payment, ExceptionType.DUPLICATE, line.amount().abs(),
                        line.currency(), payment.amount(), line.amount(),
                        "Payment " + payment.reference() + " was already settled by another " + line.type().name()
                                + " line; this line may be paid twice", now));
                continue;
            }

            BigDecimal expected = MatchingRules.expectedAmount(payment.amount(), payment.currency(), line.currency(),
                    line.fxRate());
            boolean amountOk = line.type() == SettlementLineType.SALE
                    ? MatchingRules.withinTolerance(expected, line.amount(), tolerance)
                    : MatchingRules.partialWithinTolerance(expected, line.amount(), tolerance);

            BigDecimal expectedFee = null;
            if (line.type() == SettlementLineType.SALE && line.fee() != null) {
                expectedFee = fees.expectedFee(payment.method(), line.currency(), line.amount()).orElse(null);
                BigDecimal overcharge = MatchingRules.overcharge(expectedFee, line.fee(), settings.feeTolerance());
                if (overcharge.signum() > 0) {
                    exceptions.add(exception(batch, line, payment, ExceptionType.FEE_OVERCHARGE, overcharge,
                            line.currency(), expectedFee, line.fee(),
                            "Fee " + line.fee().toPlainString() + " exceeds the expected " + expectedFee.toPlainString()
                                    + " " + line.currency() + " for " + payment.method(), now));
                }
            }
            if (line.type() == SettlementLineType.SALE) {
                settledPayments.add(payment.id());
            }
            if (amountOk) {
                updates.add(new LineUpdate(line.id(), MatchStatus.MATCHED, method, payment.id(), expectedFee));
            } else {
                updates.add(new LineUpdate(line.id(), MatchStatus.AMOUNT_MISMATCH, method, payment.id(), expectedFee));
                BigDecimal atRisk = expected == null ? line.amount().abs() : expected.abs().subtract(line.amount().abs()).abs();
                String detail = expected == null
                        ? "Settled in " + line.currency() + " but the payment is in " + payment.currency() + " and no FX rate was provided"
                        : "Settled " + line.amount().abs().toPlainString() + " " + line.currency() + " but the ledger expects "
                        + (line.type() == SettlementLineType.SALE ? "" : "at most ") + expected.toPlainString();
                exceptions.add(exception(batch, line, payment, ExceptionType.AMOUNT_MISMATCH, atRisk, line.currency(),
                        expected, line.amount(), detail, now));
            }
        }
        repository.updateLines(updates);
        repository.insertExceptions(exceptions);
        if (!settledPayments.isEmpty()) {
            repository.resolveMissingInSettlement(orgId, settledPayments, "Settled in batch " + batch.reference(), now);
        }
    }

    private LedgerPort.LedgerPayment fuzzyMatch(ReconciliationRepository.BatchKey batch, LineRecord line, Set<String> settled) {
        BigDecimal amount = line.amount().abs();
        return ledger.fuzzyCandidates(batch.organizationId(), batch.providerId(), line.currency(),
                        amount.subtract(settings.amountTolerance()), amount.add(settings.amountTolerance()),
                        line.settledAt().minus(settings.fuzzyWindow()), line.settledAt(), 5)
                .stream()
                .filter(p -> !settled.contains(p.id() + ":" + SettlementLineType.SALE.name()))
                .findFirst()
                .orElse(null);
    }

    private void flagMissingInBatchWindow(ReconciliationRepository.BatchKey batch) {
        ledger.matchedPaymentWindow(batch.id()).ifPresent(window -> {
            Instant now = clock.instant();
            List<NewException> missing = ledger.unsettledPayments(batch.organizationId(), batch.providerId(),
                            window.from(), window.to(), MISSING_BATCH_LIMIT).stream()
                    .map(p -> missingInSettlement(batch.organizationId(), batch.providerId(), batch.id(), p,
                            "Succeeded payment is absent from settlement batch " + batch.reference()
                                    + ", which covers the period it was created in", now))
                    .toList();
            repository.insertExceptions(missing);
        });
    }

    @Override
    public int requeueStalledBatches() {
        Instant now = clock.instant();
        List<ReconciliationRepository.BatchKey> stalled = repository.stalledBatches(now.minus(QUEUE_GRACE), now.minus(STALE_RUN));
        stalled.forEach(b -> matchingQueue.submit(b.organizationId(), b.id()));
        return stalled.size();
    }

    @Override
    public int flagAgedUnsettledPayments() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(settings.missingAfter());
        int flagged = 0;
        for (LedgerPort.Coverage coverage : ledger.reconciledProviders()) {
            if (!coverage.coverageStart().isBefore(cutoff)) {
                continue;
            }
            List<NewException> missing = ledger.unsettledPayments(coverage.organizationId(), coverage.providerId(),
                            coverage.coverageStart(), cutoff, MISSING_BATCH_LIMIT).stream()
                    .map(p -> missingInSettlement(coverage.organizationId(), coverage.providerId(), null, p,
                            "Succeeded payment has not appeared in any settlement after "
                                    + settings.missingAfter().toDays() + " day(s)", now))
                    .toList();
            flagged += repository.insertExceptions(missing);
        }
        return flagged;
    }

    private void notifyIfNeeded(long organizationId, BatchView batch) {
        int threshold = settings.exceptionAlertThreshold();
        if (threshold <= 0 || batch.exceptions() < threshold) {
            return;
        }
        Severity severity = batch.exceptions() >= threshold * 4 ? Severity.HIGH : Severity.MEDIUM;
        String detail = String.format("%d exception(s) across %d line(s): %d amount mismatch, %d missing in ledger, "
                        + "%d missing in settlement, %d duplicate, %d fee overcharge.%s",
                batch.exceptions(), batch.totalLines(), batch.amountMismatches(), batch.missingInLedger(),
                batch.missingInSettlement(), batch.duplicates(), batch.feeOvercharges(),
                batch.matchRate() == null ? "" : " Match rate " + batch.matchRate().toPlainString() + "%.");
        operationalEvents.publish(new OperationalEvent(organizationId, OperationalEvent.Type.RECONCILIATION_EXCEPTIONS,
                severity, "RECONCILIATION", batch.reference(), batch.providerCode(),
                "Settlement batch " + batch.reference() + " has " + batch.exceptions() + " exceptions", detail,
                "RECONCILIATION_EXCEPTIONS:" + batch.reference(), clock.instant()));
    }

    private static NewException exception(ReconciliationRepository.BatchKey batch, LineRecord line,
                                           LedgerPort.LedgerPayment payment, ExceptionType type, BigDecimal atRisk,
                                           String currency, BigDecimal expected, BigDecimal actual, String detail,
                                           Instant now) {
        return new NewException(References.next("REX"), batch.organizationId(), batch.providerId(), batch.id(),
                line.id(), payment == null ? null : payment.id(), type, atRisk, currency, expected, actual,
                payment == null ? null : payment.reference(), line.providerReference(), truncate(detail), now);
    }

    private static NewException missingInSettlement(long organizationId, long providerId, Long batchId,
                                                    LedgerPort.LedgerPayment p, String detail, Instant now) {
        return new NewException(References.next("REX"), organizationId, providerId, batchId, null, p.id(),
                ExceptionType.MISSING_IN_SETTLEMENT, p.amount(), p.currency(), p.amount(), null, p.reference(),
                p.providerReference(), truncate(detail), now);
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
