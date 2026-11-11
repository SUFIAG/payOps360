package com.payops.reconciliation.application.service;

import com.payops.reconciliation.application.port.in.BatchView;
import com.payops.reconciliation.application.port.in.ExceptionView;
import com.payops.reconciliation.application.port.in.ReconciliationQueryUseCase;
import com.payops.reconciliation.application.port.out.LedgerPort;
import com.payops.reconciliation.application.port.out.ReconciliationRepository;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReconciliationQueryService implements ReconciliationQueryUseCase {

    private static final long UNKNOWN = -1L;

    private final ReconciliationRepository repository;
    private final LedgerPort ledger;
    private final Clock clock;

    @Override
    public PageResult<BatchView> batches(long organizationId, String providerCode, PageQuery page) {
        return repository.batches(organizationId, providerId(organizationId, providerCode), page);
    }

    @Override
    public BatchView batch(long organizationId, String reference) {
        return repository.batchKey(organizationId, reference)
                .flatMap(key -> repository.batch(organizationId, key.id()))
                .orElseThrow(() -> new NotFoundException("Settlement batch", reference));
    }

    @Override
    public PageResult<ExceptionView> exceptions(long organizationId, ExceptionFilter filter, PageQuery page) {
        Long batchId = filter.batchReference() == null ? null
                : repository.batchKey(organizationId, filter.batchReference()).map(ReconciliationRepository.BatchKey::id)
                .orElse(UNKNOWN);
        return repository.exceptions(organizationId, new ReconciliationRepository.ExceptionQuery(filter.types(),
                filter.statuses(), providerId(organizationId, filter.providerCode()), batchId, filter.assignee()), page);
    }

    @Override
    public ExceptionDetails exception(long organizationId, String reference) {
        ReconciliationRepository.ExceptionState state = repository.exceptionState(organizationId, reference)
                .orElseThrow(() -> new NotFoundException("Reconciliation exception", reference));
        ExceptionView view = repository.exception(organizationId, state.id()).orElseThrow();
        LineView line = state.lineId() == null ? null : repository.line(state.lineId())
                .map(l -> new LineView(l.lineNo(), l.type(), l.providerReference(), l.amount(), l.currency(), l.fee(),
                        l.expectedFee(), l.fxRate(), l.settledAt(), l.matchStatus(), l.matchMethod()))
                .orElse(null);
        PaymentSnapshot payment = state.paymentId() == null ? null : ledger.findById(organizationId, state.paymentId())
                .map(p -> new PaymentSnapshot(p.reference(), p.merchantReference(), p.amount(), p.currency(), p.method(),
                        p.status(), ledger.providerById(organizationId, p.providerId())
                        .map(LedgerPort.ProviderRef::code).orElse(null), p.createdAt()))
                .orElse(null);
        return new ExceptionDetails(view, line, payment);
    }

    @Override
    public Summary summary(long organizationId, int days) {
        int window = clamp(days);
        return repository.summary(organizationId, window, since(window));
    }

    @Override
    public List<TrendPoint> matchRateTrend(long organizationId, int days) {
        return repository.trend(organizationId, since(clamp(days)));
    }

    private Instant since(int days) {
        return clock.instant().truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(days - 1L));
    }

    private static int clamp(int days) {
        return Math.max(1, Math.min(days, 365));
    }

    private Long providerId(long organizationId, String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        return ledger.provider(organizationId, providerCode).map(LedgerPort.ProviderRef::id).orElse(UNKNOWN);
    }
}
