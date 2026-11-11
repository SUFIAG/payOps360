package com.payops.payment.adapter.out.persistence;

import com.payops.payment.application.port.out.PaymentRepository;
import com.payops.payment.domain.Money;
import com.payops.payment.domain.Payment;
import com.payops.payment.domain.PaymentEvent;
import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.payment.domain.RiskAssessment;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository payments;
    private final PaymentEventJpaRepository events;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = payment.id() == null ? newEntity(payment) : payments.findById(payment.id()).orElseThrow();
        entity.setStatus(payment.status().name());
        entity.setPreviousStatus(payment.previousStatus() == null ? null : payment.previousStatus().name());
        entity.setStatusChangedAt(payment.statusChangedAt());
        entity.setFailureCode(payment.failureCode());
        entity.setFailureMessage(payment.failureMessage());
        entity.setLatencyMs(payment.latencyMs());
        entity.setRetryCount(payment.retryCount());
        entity.setStuck(payment.stuck());
        entity.setRiskScore(payment.risk().score());
        entity.setRiskLevel(payment.risk().level().name());
        entity.setRiskFactors(new ArrayList<>(payment.risk().factors()));
        entity.setUpdatedAt(payment.updatedAt());
        PaymentEntity saved = payments.save(entity);

        List<PaymentEventEntity> eventEntities = payment.pendingEvents().stream().map(e -> {
            PaymentEventEntity ee = new PaymentEventEntity();
            ee.setPaymentId(saved.getId());
            ee.setFromStatus(e.fromStatus() == null ? null : e.fromStatus().name());
            ee.setToStatus(e.toStatus().name());
            ee.setReason(e.reason());
            ee.setErrorCode(e.errorCode());
            ee.setActor(e.actor());
            ee.setOccurredAt(e.occurredAt());
            return ee;
        }).toList();
        events.saveAll(eventEntities);
        payment.clearPendingEvents();
        return toDomain(saved);
    }

    @Override
    public Optional<Payment> findByReference(long organizationId, String reference) {
        return payments.findByOrganizationIdAndReference(organizationId, reference).map(PaymentPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(long organizationId, String idempotencyKey) {
        return payments.findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey).map(PaymentPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<Payment> search(long organizationId, PaymentSearchCriteria criteria, PageQuery page) {
        Page<PaymentEntity> result = payments.findAll(specification(organizationId, criteria),
                PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(result.getContent().stream().map(PaymentPersistenceAdapter::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    @Override
    public List<PaymentEvent> timeline(long paymentId) {
        return events.findByPaymentIdOrderByOccurredAtAscIdAsc(paymentId).stream()
                .map(e -> new PaymentEvent(e.getFromStatus() == null ? null : PaymentStatus.valueOf(e.getFromStatus()),
                        PaymentStatus.valueOf(e.getToStatus()), e.getReason(), e.getErrorCode(), e.getActor(),
                        e.getOccurredAt()))
                .toList();
    }

    @Override
    public List<Payment> findInStatusSince(PaymentStatus status, Instant before, int limit) {
        return payments.findStuckCandidates(status.name(), before, PageRequest.of(0, limit)).stream()
                .map(PaymentPersistenceAdapter::toDomain).toList();
    }

    private static Specification<PaymentEntity> specification(long organizationId, PaymentSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), organizationId));
            if (c.statuses() != null && !c.statuses().isEmpty()) {
                p.add(root.get("status").in(c.statuses().stream().map(Enum::name).toList()));
            }
            if (c.providerId() != null) {
                p.add(cb.equal(root.get("providerId"), c.providerId()));
            }
            if (c.method() != null) {
                p.add(cb.equal(root.get("method"), c.method().name()));
            }
            if (c.minRiskLevel() != null) {
                List<String> levels = Arrays.stream(RiskAssessment.RiskLevel.values())
                        .filter(l -> l.ordinal() >= c.minRiskLevel().ordinal()).map(Enum::name).toList();
                p.add(root.get("riskLevel").in(levels));
            }
            if (c.currency() != null) {
                p.add(cb.equal(root.get("currency"), c.currency().toUpperCase(Locale.ROOT)));
            }
            if (c.minAmount() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("amount"), c.minAmount()));
            }
            if (c.maxAmount() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("amount"), c.maxAmount()));
            }
            if (c.customerRef() != null) {
                p.add(cb.equal(root.get("customerRef"), c.customerRef()));
            }
            if (c.country() != null) {
                p.add(cb.equal(root.get("country"), c.country().toUpperCase(Locale.ROOT)));
            }
            if (c.stuck() != null) {
                p.add(cb.equal(root.get("stuck"), c.stuck()));
            }
            if (c.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.from()));
            }
            if (c.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), c.to()));
            }
            if (c.text() != null && !c.text().isBlank()) {
                String like = "%" + c.text().trim().toUpperCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.upper(root.get("reference")), like, '\\'),
                        cb.like(cb.upper(root.get("merchantReference")), like, '\\'),
                        cb.like(cb.upper(root.get("customerRef")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static PaymentEntity newEntity(Payment payment) {
        PaymentEntity e = new PaymentEntity();
        e.setOrganizationId(payment.organizationId());
        e.setReference(payment.reference());
        e.setIdempotencyKey(payment.idempotencyKey());
        e.setMerchantReference(payment.merchantReference());
        e.setAmount(payment.money().amount());
        e.setCurrency(payment.money().currency());
        e.setProviderId(payment.providerId());
        e.setCustomerRef(payment.customerRef());
        e.setMethod(payment.method().name());
        e.setCardLast4(payment.cardLast4());
        e.setCountry(payment.country());
        e.setCreatedAt(payment.createdAt());
        return e;
    }

    private static Payment toDomain(PaymentEntity e) {
        return new Payment(e.getId(), e.getOrganizationId(), e.getReference(), e.getIdempotencyKey(),
                e.getMerchantReference(), new Money(e.getAmount(), e.getCurrency()), e.getProviderId(),
                e.getCustomerRef(), PaymentMethod.valueOf(e.getMethod()), e.getCardLast4(), e.getCountry(),
                PaymentStatus.valueOf(e.getStatus()),
                e.getPreviousStatus() == null ? null : PaymentStatus.valueOf(e.getPreviousStatus()),
                e.getStatusChangedAt(), e.getFailureCode(), e.getFailureMessage(), e.getLatencyMs(), e.getRetryCount(),
                e.isStuck(),
                new RiskAssessment(e.getRiskScore(), RiskAssessment.RiskLevel.valueOf(e.getRiskLevel()),
                        e.getRiskFactors() == null ? List.of() : e.getRiskFactors()),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}
