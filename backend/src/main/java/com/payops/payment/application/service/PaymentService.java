package com.payops.payment.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.payment.application.port.input.PaymentCommandUseCase;
import com.payops.payment.application.port.input.PaymentQueryUseCase;
import com.payops.payment.application.port.input.PaymentView;
import com.payops.payment.application.port.input.StuckPaymentDetectionUseCase;
import com.payops.payment.application.port.output.PaymentRepository;
import com.payops.payment.application.port.output.PaymentSignalPort;
import com.payops.payment.application.port.output.ProviderDirectory;
import com.payops.payment.application.port.output.RiskScoringPort;
import com.payops.payment.domain.Money;
import com.payops.payment.domain.Payment;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.payment.domain.RiskAssessment;
import com.payops.shared.application.Actor;
import com.payops.shared.application.LiveEventPublisher;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService implements PaymentCommandUseCase, PaymentQueryUseCase, StuckPaymentDetectionUseCase {

    private static final int STUCK_BATCH = 500;

    private final PaymentRepository payments;
    private final ProviderDirectory providers;
    private final RiskScoringPort riskScoring;
    private final PaymentSignalPort signals;
    private final LiveEventPublisher liveEvents;
    private final AuditTrail auditTrail;
    private final PaymentSettings settings;
    private final Clock clock;

    // ---------------------------------------------------------------- commands

    @Override
    public IngestResult ingest(Actor actor, IngestCommand command, RequestContext context) {
        if (command.idempotencyKey() != null) {
            Optional<Payment> existing = payments.findByIdempotencyKey(actor.organizationId(), command.idempotencyKey());
            if (existing.isPresent()) {
                return new IngestResult(view(existing.get()), true);
            }
        }
        ProviderDirectory.ProviderRef provider = providers.findByCode(actor.organizationId(), command.providerCode())
                .orElseThrow(() -> new NotFoundException("Provider", command.providerCode()));
        if (!provider.active()) {
            throw new DomainException("PROVIDER_INACTIVE", "Provider '" + provider.code() + "' is not active");
        }

        Instant now = clock.instant();
        Payment payment = Payment.initiate(actor.organizationId(), command.idempotencyKey(), command.merchantReference(),
                new Money(command.amount(), command.currency()), provider.id(), command.customerRef(), command.method(),
                command.cardLast4(), command.country(), actor.email(), now);

        RiskAssessment risk = riskScoring.assess(new RiskScoringPort.RiskInput(actor.organizationId(),
                payment.customerRef(), payment.money().amount(), payment.money().currency(), payment.method(),
                payment.country(), provider.id(), now));
        payment.assessRisk(risk);

        if (command.initialOutcome() != null && command.initialOutcome() != PaymentStatus.INITIATED) {
            applyOutcome(payment, command.initialOutcome(), command.failureCode(), command.failureMessage(),
                    command.latencyMs(), actor.email(), now);
        }

        Payment saved = payments.save(payment);
        PaymentView view = PaymentView.of(saved, provider);
        liveEvents.publish(actor.organizationId(), "payment.created", view);
        if (risk.level() == RiskAssessment.RiskLevel.CRITICAL) {
            signals.highRiskPayment(actor.organizationId(), provider.id(), view);
        }
        if (!actor.isSystem()) {
            audit(actor, AuditAction.PAYMENT_CREATED, saved, context, Map.of("status", saved.status().name()));
        }
        return new IngestResult(view, false);
    }

    @Override
    public PaymentView changeStatus(Actor actor, String reference, StatusChangeCommand command, RequestContext context) {
        Payment payment = load(actor.organizationId(), reference);
        PaymentStatus from = payment.status();
        payment.transitionTo(command.status(), reasonOr(command.reason(), "Status updated"), command.failureCode(),
                command.failureMessage(), command.latencyMs(), actor.email(), clock.instant());
        PaymentView view = view(payments.save(payment));
        liveEvents.publish(actor.organizationId(), "payment.updated", view);
        if (!actor.isSystem()) {
            audit(actor, AuditAction.PAYMENT_STATUS_CHANGED, payment, context,
                    Map.of("from", from.name(), "to", command.status().name()));
        }
        return view;
    }

    @Override
    public PaymentView retry(Actor actor, String reference, RequestContext context) {
        Payment payment = load(actor.organizationId(), reference);
        if (!payment.canRetry()) {
            throw new DomainException("NOT_RETRYABLE", "Only failed payments below the retry limit can be retried");
        }
        return changeStatus(actor, reference, new StatusChangeCommand(PaymentStatus.RETRY_PENDING,
                "Retry requested by operator", null, null, null), context);
    }

    // ----------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public PaymentDetails get(long organizationId, String reference) {
        Payment payment = load(organizationId, reference);
        return new PaymentDetails(view(payment), payments.timeline(payment.id()),
                payment.status().allowedTransitions(), payment.canRetry());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PaymentView> search(long organizationId, PaymentSearchCriteria criteria, PageQuery page) {
        Map<Long, ProviderDirectory.ProviderRef> cache = new HashMap<>();
        return payments.search(organizationId, criteria, page).map(p -> PaymentView.of(p,
                cache.computeIfAbsent(p.providerId(), id -> providers.findById(organizationId, id).orElse(null))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> providerIdFor(long organizationId, String providerCode) {
        return providers.findByCode(organizationId, providerCode).map(ProviderDirectory.ProviderRef::id);
    }

    // ------------------------------------------------------------- monitoring

    @Override
    public int detectStuckPayments() {
        Instant now = clock.instant();
        Map<Long, Integer> flaggedPerTenant = new HashMap<>();
        for (Map.Entry<PaymentStatus, Duration> threshold : settings.stuckThresholds().entrySet()) {
            List<Payment> candidates = payments.findInStatusSince(threshold.getKey(), now.minus(threshold.getValue()), STUCK_BATCH);
            for (Payment payment : candidates) {
                if (payment.evaluateStuck(threshold.getValue(), now)) {
                    payments.save(payment);
                    flaggedPerTenant.merge(payment.organizationId(), 1, Integer::sum);
                }
            }
        }
        flaggedPerTenant.forEach(signals::stuckPayments);
        return flaggedPerTenant.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ------------------------------------------------------------------------

    /**
     * Walks the state machine from INITIATED to the outcome reported at ingestion
     * (e.g. a provider webhook that already says SETTLED or FAILED).
     */
    private void applyOutcome(Payment payment, PaymentStatus outcome, String failureCode, String failureMessage,
                              Integer latencyMs, String actor, Instant now) {
        List<PaymentStatus> path = switch (outcome) {
            case AUTHORIZED -> List.of(PaymentStatus.AUTHORIZED);
            case CAPTURED -> List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED);
            case PROCESSING -> List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.PROCESSING);
            case SETTLED -> List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.SETTLED);
            case FAILED -> List.of(PaymentStatus.FAILED);
            case CANCELLED -> List.of(PaymentStatus.CANCELLED);
            default -> throw new DomainException("INVALID_INITIAL_STATUS", "Cannot ingest a payment as " + outcome);
        };
        for (PaymentStatus step : path) {
            payment.transitionTo(step, "Reported by provider", step.isFailure() ? failureCode : null,
                    step.isFailure() ? failureMessage : null, latencyMs, actor, now);
        }
    }

    private Payment load(long organizationId, String reference) {
        return payments.findByReference(organizationId, reference)
                .orElseThrow(() -> new NotFoundException("Payment", reference));
    }

    private PaymentView view(Payment payment) {
        return PaymentView.of(payment, providers.findById(payment.organizationId(), payment.providerId()).orElse(null));
    }

    private static String reasonOr(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private void audit(Actor actor, AuditAction action, Payment payment, RequestContext context, Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, "PAYMENT",
                payment.reference(), AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(), details,
                clock.instant()));
    }
}
