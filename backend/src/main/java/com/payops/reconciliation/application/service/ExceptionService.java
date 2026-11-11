package com.payops.reconciliation.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.reconciliation.application.port.in.ExceptionCommandUseCase;
import com.payops.reconciliation.application.port.in.ExceptionView;
import com.payops.reconciliation.application.port.out.LedgerPort;
import com.payops.reconciliation.application.port.out.ReconciliationRepository;
import com.payops.reconciliation.application.port.out.ReconciliationRepository.ExceptionState;
import com.payops.reconciliation.domain.ExceptionStatus;
import com.payops.reconciliation.domain.MatchMethod;
import com.payops.reconciliation.domain.MatchStatus;
import com.payops.reconciliation.domain.ResolutionReason;
import com.payops.reconciliation.domain.SettlementLineType;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception queue workflow: assign, resolve, manually match, write off.
 * Terminal exceptions (RESOLVED, WRITTEN_OFF) are immutable.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ExceptionService implements ExceptionCommandUseCase {

    private final ReconciliationRepository repository;
    private final LedgerPort ledger;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    public ExceptionView assign(Actor actor, String reference, String assignee, RequestContext context) {
        ExceptionState state = openException(actor, reference);
        String owner = assignee == null || assignee.isBlank() ? actor.email() : assignee.trim();
        ExceptionStatus next = state.status() == ExceptionStatus.OPEN ? ExceptionStatus.INVESTIGATING : state.status();
        Instant now = clock.instant();
        save(state.with(next, owner, null, null, null, null), now);
        audit(actor, AuditAction.RECON_EXCEPTION_ASSIGNED, reference, context, now,
                "assignee", owner, "previousAssignee", String.valueOf(state.assignee()), "status", next.name());
        return view(state);
    }

    @Override
    public ExceptionView resolve(Actor actor, String reference, ResolutionReason reason, String note,
                                 RequestContext context) {
        ExceptionState state = openException(actor, reference);
        requireNote(note);
        if (reason == null) {
            throw new DomainException("RESOLUTION_REASON_REQUIRED", "A resolution reason is required");
        }
        Instant now = clock.instant();
        save(state.with(ExceptionStatus.RESOLVED, state.assignee(), reason, note.trim(), actor.email(), now), now);
        audit(actor, AuditAction.RECON_EXCEPTION_RESOLVED, reference, context, now,
                "type", state.type().name(), "reason", reason.name());
        return view(state);
    }

    @Override
    public ExceptionView manualMatch(Actor actor, String reference, String paymentReference, String note,
                                     RequestContext context) {
        ExceptionState state = openException(actor, reference);
        if (!state.type().hasSettlementLine() || state.lineId() == null) {
            throw new DomainException("MANUAL_MATCH_NOT_SUPPORTED",
                    "Only exceptions raised from a settlement line can be matched to a payment");
        }
        LedgerPort.LedgerPayment payment = ledger.findByReference(actor.organizationId(), paymentReference)
                .orElseThrow(() -> new NotFoundException("Payment", paymentReference));
        ReconciliationRepository.LineRecord line = repository.line(state.lineId())
                .orElseThrow(() -> new NotFoundException("Settlement line", state.lineId()));
        boolean sameAsLine = Long.valueOf(payment.id()).equals(line.paymentId());
        if (!sameAsLine && repository.settledPaymentKeys(List.of(payment.id()))
                .contains(payment.id() + ":" + line.type().name())) {
            throw new DomainException("PAYMENT_ALREADY_SETTLED",
                    "Payment " + payment.reference() + " is already settled by another " + line.type().name() + " line");
        }
        Instant now = clock.instant();
        repository.updateLines(List.of(new ReconciliationRepository.LineUpdate(line.id(), MatchStatus.MATCHED,
                MatchMethod.MANUAL, payment.id(), line.expectedFee())));
        if (line.type() == SettlementLineType.SALE) {
            repository.resolveMissingInSettlement(actor.organizationId(), List.of(payment.id()),
                    "Manually matched to settlement line " + line.lineNo() + " by " + actor.email(), now);
        }
        String resolution = note == null || note.isBlank() ? "Manually matched to payment " + payment.reference() : note.trim();
        save(state.with(ExceptionStatus.RESOLVED, state.assignee(), ResolutionReason.MANUAL_MATCH, resolution,
                actor.email(), now), now);
        audit(actor, AuditAction.RECON_EXCEPTION_MATCHED, reference, context, now,
                "payment", payment.reference(), "line", String.valueOf(line.lineNo()));
        return view(state);
    }

    @Override
    public ExceptionView writeOff(Actor actor, String reference, String note, RequestContext context) {
        ExceptionState state = openException(actor, reference);
        requireNote(note);
        Instant now = clock.instant();
        save(state.with(ExceptionStatus.WRITTEN_OFF, state.assignee(), ResolutionReason.OTHER, note.trim(),
                actor.email(), now), now);
        audit(actor, AuditAction.RECON_EXCEPTION_WRITTEN_OFF, reference, context, now, "type", state.type().name());
        return view(state);
    }

    // ------------------------------------------------------------------

    private ExceptionState openException(Actor actor, String reference) {
        ExceptionState state = repository.exceptionState(actor.organizationId(), reference)
                .orElseThrow(() -> new NotFoundException("Reconciliation exception", reference));
        if (state.status().isTerminal()) {
            throw new DomainException("EXCEPTION_CLOSED", "Exception " + reference + " is already " + state.status());
        }
        return state;
    }

    private void save(ExceptionState state, Instant now) {
        if (!repository.updateException(state, now)) {
            throw new ConflictException("CONCURRENT_UPDATE", "The exception was modified concurrently. Reload and retry.");
        }
    }

    private ExceptionView view(ExceptionState state) {
        return repository.exception(state.organizationId(), state.id()).orElseThrow();
    }

    private static void requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new DomainException("NOTE_REQUIRED", "A note explaining the decision is required");
        }
    }

    private void audit(Actor actor, AuditAction action, String reference, RequestContext context, Instant now,
                       String... details) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < details.length; i += 2) {
            map.put(details[i], details[i + 1]);
        }
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action,
                "RECON_EXCEPTION", reference, AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(), map, now));
    }
}
