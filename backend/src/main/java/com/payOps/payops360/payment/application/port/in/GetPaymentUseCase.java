package com.payops.payops360.payment.application.port.in;

import com.payops.payops360.payment.domain.model.Payment;

/**
 * INPUT PORT
 * Get payment by ID use case.
 */
public interface GetPaymentUseCase {

    /**
     * Get payment by database ID
     */
    Payment getById(Long id);

    /**
     * Get payment by business identifier
     */
    Payment getByPaymentId(String paymentId);
}

