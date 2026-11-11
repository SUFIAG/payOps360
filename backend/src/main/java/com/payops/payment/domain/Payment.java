package com.payops.payment.domain;

import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.References;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Payment aggregate root. All state changes go through {@link #transitionTo}
 * which enforces the lifecycle state machine and records an event.
 */
public class Payment {

    public static final int MAX_RETRIES = 3;
    private static final Pattern LAST4 = Pattern.compile("^\\d{4}$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    private final Long id;
    private final long organizationId;
    private final String reference;
    private final String idempotencyKey;
    private final String merchantReference;
    private final Money money;
    private final long providerId;
    private final String customerRef;
    private final PaymentMethod method;
    private final String cardLast4;
    private final String country;
    private PaymentStatus status;
    private PaymentStatus previousStatus;
    private Instant statusChangedAt;
    private String failureCode;
    private String failureMessage;
    private Integer latencyMs;
    private int retryCount;
    private boolean stuck;
    private RiskAssessment risk;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;
    private final List<PaymentEvent> newEvents = new ArrayList<>();

    @SuppressWarnings("java:S107")
    public Payment(Long id, long organizationId, String reference, String idempotencyKey, String merchantReference,
                   Money money, long providerId, String customerRef, PaymentMethod method, String cardLast4,
                   String country, PaymentStatus status, PaymentStatus previousStatus, Instant statusChangedAt,
                   String failureCode, String failureMessage, Integer latencyMs, int retryCount, boolean stuck,
                   RiskAssessment risk, Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
        this.merchantReference = merchantReference;
        this.money = money;
        this.providerId = providerId;
        this.customerRef = customerRef;
        this.method = method;
        this.cardLast4 = cardLast4;
        this.country = country;
        this.status = status;
        this.previousStatus = previousStatus;
        this.statusChangedAt = statusChangedAt;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.latencyMs = latencyMs;
        this.retryCount = retryCount;
        this.stuck = stuck;
        this.risk = risk;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    @SuppressWarnings("java:S107")
    public static Payment initiate(long organizationId, String idempotencyKey, String merchantReference, Money money,
                                   long providerId, String customerRef, PaymentMethod method, String cardLast4,
                                   String country, String actor, Instant now) {
        if (customerRef == null || customerRef.isBlank()) {
            throw new DomainException("INVALID_CUSTOMER", "Customer reference is required");
        }
        if (cardLast4 != null && !LAST4.matcher(cardLast4).matches()) {
            throw new DomainException("INVALID_CARD", "Only the last 4 digits of a card may be supplied");
        }
        if (method == PaymentMethod.CARD && cardLast4 == null) {
            throw new DomainException("INVALID_CARD", "Card payments require the last 4 digits");
        }
        String normalizedCountry = country == null ? null : country.toUpperCase();
        if (normalizedCountry != null && !COUNTRY.matcher(normalizedCountry).matches()) {
            throw new DomainException("INVALID_COUNTRY", "Country must be an ISO-3166 alpha-2 code");
        }
        Payment payment = new Payment(null, organizationId, References.next("PAY"), idempotencyKey,
                merchantReference, money, providerId, customerRef.trim(), method, cardLast4, normalizedCountry,
                PaymentStatus.INITIATED, null, now, null, null, null, 0, false, RiskAssessment.none(), now, now, 0);
        payment.newEvents.add(new PaymentEvent(null, PaymentStatus.INITIATED, "Payment initiated", null, actor, now));
        return payment;
    }

    /**
     * Moves the payment to {@code target}. Re-applying the current status is a
     * no-op so that duplicate provider webhooks are idempotent.
     */
    public void transitionTo(PaymentStatus target, String reason, String errorCode, String errorMessage,
                             Integer observedLatencyMs, String actor, Instant now) {
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new DomainException("ILLEGAL_TRANSITION",
                    "Payment " + reference + " cannot move from " + status + " to " + target);
        }
        if (target == PaymentStatus.RETRY_PENDING) {
            if (retryCount >= MAX_RETRIES) {
                throw new DomainException("RETRY_LIMIT", "Payment " + reference + " reached the retry limit");
            }
            retryCount++;
        }
        if (target.isFailure()) {
            failureCode = errorCode;
            failureMessage = errorMessage;
        } else if (target.isAccepted()) {
            failureCode = null;
            failureMessage = null;
        }
        if (observedLatencyMs != null) {
            latencyMs = observedLatencyMs;
        }
        newEvents.add(new PaymentEvent(status, target, reason, errorCode, actor, now));
        previousStatus = status;
        status = target;
        statusChangedAt = now;
        stuck = false;
        updatedAt = now;
    }

    /**
     * Flags a payment that has stayed in an in-flight state longer than allowed.
     * @return true if the flag changed
     */
    public boolean evaluateStuck(Duration threshold, Instant now) {
        boolean shouldBeStuck = status.isInFlight() && threshold != null
                && statusChangedAt.plus(threshold).isBefore(now);
        if (shouldBeStuck != stuck) {
            stuck = shouldBeStuck;
            updatedAt = now;
            return true;
        }
        return false;
    }

    public void assessRisk(RiskAssessment assessment) {
        this.risk = assessment;
    }

    public boolean canRetry() {
        return status == PaymentStatus.FAILED && retryCount < MAX_RETRIES;
    }

    /** Events raised since the aggregate was loaded; drained by the repository. */
    public List<PaymentEvent> pendingEvents() {
        return Collections.unmodifiableList(newEvents);
    }

    public void clearPendingEvents() {
        newEvents.clear();
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public String reference() { return reference; }
    public String idempotencyKey() { return idempotencyKey; }
    public String merchantReference() { return merchantReference; }
    public Money money() { return money; }
    public long providerId() { return providerId; }
    public String customerRef() { return customerRef; }
    public PaymentMethod method() { return method; }
    public String cardLast4() { return cardLast4; }
    public String country() { return country; }
    public PaymentStatus status() { return status; }
    public PaymentStatus previousStatus() { return previousStatus; }
    public Instant statusChangedAt() { return statusChangedAt; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }
    public Integer latencyMs() { return latencyMs; }
    public int retryCount() { return retryCount; }
    public boolean stuck() { return stuck; }
    public RiskAssessment risk() { return risk; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
