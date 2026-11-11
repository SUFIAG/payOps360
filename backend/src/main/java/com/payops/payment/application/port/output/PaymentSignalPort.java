package com.payops.payment.application.port.output;

import com.payops.payment.application.port.input.PaymentView;

/**
 * Operational signals the payment module emits for alerting.
 */
public interface PaymentSignalPort {

    void highRiskPayment(long organizationId, long providerId, PaymentView payment);

    void stuckPayments(long organizationId, int count);
}
