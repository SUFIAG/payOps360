package com.payops.payops360.payment.application.port.in;

import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * INPUT PORT
 * List payments with filtering use case.
 */
public interface ListPaymentsUseCase {

    /**
     * List all payments with pagination
     */
    Page<Payment> listAll(Pageable pageable);

    /**
     * List payments by status
     */
    Page<Payment> listByStatus(PaymentStatus status, Pageable pageable);

    /**
     * List payments by provider
     */
    Page<Payment> listByProvider(String providerId, Pageable pageable);

    /**
     * List payments by customer
     */
    Page<Payment> listByCustomer(String customerId, Pageable pageable);
}

