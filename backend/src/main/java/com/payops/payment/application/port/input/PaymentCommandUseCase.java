package com.payops.payment.application.port.input;

import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.PaymentStatus;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.math.BigDecimal;

public interface PaymentCommandUseCase {

    /**
     * Records a payment reported by a provider or merchant system. Calls with
     * the same idempotency key return the original payment instead of creating
     * a duplicate.
     */
    IngestResult ingest(Actor actor, IngestCommand command, RequestContext context);

    PaymentView changeStatus(Actor actor, String reference, StatusChangeCommand command, RequestContext context);

    PaymentView retry(Actor actor, String reference, RequestContext context);

    record IngestCommand(String idempotencyKey, String merchantReference, BigDecimal amount, String currency,
                         String providerCode, String customerRef, PaymentMethod method, String cardLast4,
                         String country, PaymentStatus initialOutcome, String failureCode, String failureMessage,
                         Integer latencyMs) {
    }

    record StatusChangeCommand(PaymentStatus status, String reason, String failureCode, String failureMessage,
                               Integer latencyMs) {
    }

    record IngestResult(PaymentView payment, boolean replayed) {
    }
}
