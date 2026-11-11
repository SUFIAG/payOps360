package com.payops.payment.application.port.output;

import com.payops.payment.domain.Payment;
import com.payops.payment.domain.PaymentEvent;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    /** Persists the aggregate together with its pending lifecycle events. */
    Payment save(Payment payment);

    Optional<Payment> findByReference(long organizationId, String reference);

    Optional<Payment> findByIdempotencyKey(long organizationId, String idempotencyKey);

    PageResult<Payment> search(long organizationId, PaymentSearchCriteria criteria, PageQuery page);

    List<PaymentEvent> timeline(long paymentId);

    /** In-flight payments (any tenant) whose status has not changed since {@code before}. */
    List<Payment> findInStatusSince(PaymentStatus status, Instant before, int limit);
}
