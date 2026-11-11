package com.payops.payment.domain;

import com.payops.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Test
    void happyPathRecordsEveryTransition() {
        Payment p = card();
        p.transitionTo(PaymentStatus.AUTHORIZED, "auth", null, null, 320, "psp", NOW);
        p.transitionTo(PaymentStatus.CAPTURED, "capture", null, null, null, "psp", NOW);
        p.transitionTo(PaymentStatus.SETTLED, "settle", null, null, null, "psp", NOW);
        assertThat(p.status()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(p.pendingEvents()).extracting(PaymentEvent::toStatus).containsExactly(
                PaymentStatus.INITIATED, PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.SETTLED);
        assertThat(p.latencyMs()).isEqualTo(320);
    }

    @Test
    void illegalTransitionsAreRejected() {
        Payment p = card();
        assertThatThrownBy(() -> p.transitionTo(PaymentStatus.REFUNDED, "x", null, null, null, "op", NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("cannot move");
        assertThat(PaymentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(PaymentStatus.SETTLED.allowedTransitions()).containsExactlyInAnyOrder(PaymentStatus.REFUNDED, PaymentStatus.CHARGEBACK);
    }

    @Test
    void duplicateWebhooksAreIdempotent() {
        Payment p = card();
        p.transitionTo(PaymentStatus.AUTHORIZED, "auth", null, null, null, "psp", NOW);
        p.transitionTo(PaymentStatus.AUTHORIZED, "auth again", null, null, null, "psp", NOW);
        assertThat(p.pendingEvents()).hasSize(2);
    }

    @Test
    void retriesAreCappedAndFailureDetailsCleared() {
        Payment p = card();
        p.transitionTo(PaymentStatus.FAILED, "declined", "DO_NOT_HONOR", "declined", null, "psp", NOW);
        assertThat(p.failureCode()).isEqualTo("DO_NOT_HONOR");
        for (int i = 0; i < Payment.MAX_RETRIES; i++) {
            assertThat(p.canRetry()).isTrue();
            p.transitionTo(PaymentStatus.RETRY_PENDING, "retry", null, null, null, "op", NOW);
            p.transitionTo(PaymentStatus.RETRY_IN_PROGRESS, "retry", null, null, null, "op", NOW);
            p.transitionTo(PaymentStatus.AUTHORIZED, "ok", null, null, null, "psp", NOW);
            assertThat(p.failureCode()).isNull();
            p.transitionTo(PaymentStatus.FAILED, "declined", "DO_NOT_HONOR", "declined", null, "psp", NOW);
        }
        assertThat(p.canRetry()).isFalse();
        assertThatThrownBy(() -> p.transitionTo(PaymentStatus.RETRY_PENDING, "retry", null, null, null, "op", NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void stuckDetectionOnlyAppliesToInFlightPayments() {
        Payment p = card();
        p.transitionTo(PaymentStatus.AUTHORIZED, "auth", null, null, null, "psp", NOW);
        assertThat(p.evaluateStuck(Duration.ofMinutes(10), NOW.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(p.evaluateStuck(Duration.ofMinutes(10), NOW.plus(Duration.ofMinutes(11)))).isTrue();
        assertThat(p.stuck()).isTrue();
        p.transitionTo(PaymentStatus.CAPTURED, "capture", null, null, null, "psp", NOW.plus(Duration.ofMinutes(12)));
        assertThat(p.stuck()).isFalse();
    }

    @Test
    void moneyRespectsCurrencyMinorUnits() {
        assertThat(new Money(new BigDecimal("10.5"), "usd").amount()).isEqualByComparingTo("10.50");
        assertThat(new Money(new BigDecimal("1000"), "JPY").amount().scale()).isZero();
        assertThatThrownBy(() -> new Money(new BigDecimal("10.555"), "USD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("1.5"), "JPY")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "USD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "XYZ")).isInstanceOf(DomainException.class);
    }

    @Test
    void cardPaymentsAcceptOnlyLastFourDigits() {
        assertThatThrownBy(() -> Payment.initiate(1, null, null, Money.of("10", "USD"), 1, "C1", PaymentMethod.CARD,
                "4111111111111111", "US", "op", NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Payment.initiate(1, null, null, Money.of("10", "USD"), 1, "C1", PaymentMethod.CARD,
                null, "US", "op", NOW)).isInstanceOf(DomainException.class);
    }

    private static Payment card() {
        return Payment.initiate(1, "idem-1", "ORD-1", Money.of("125.00", "GBP"), 7, "CUS-1", PaymentMethod.CARD,
                "4242", "pk", "api", NOW);
    }
}
