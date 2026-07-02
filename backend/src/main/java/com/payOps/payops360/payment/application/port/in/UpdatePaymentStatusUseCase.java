package com.payops.payops360.payment.application.port.in;

import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentStatus;

/**
 * INPUT PORT
 * Update payment status use case.
 */
public interface UpdatePaymentStatusUseCase {

    /**
     * Update payment status
     */
    Payment updateStatus(UpdateStatusCommand command);

    /**
     * Command for status update
     */
    record UpdateStatusCommand(
            String paymentId,
            PaymentStatus newStatus,
            String reason,
            String errorCode,
            String errorMessage
    ) {
        public UpdateStatusCommand {
            if (paymentId == null || paymentId.isBlank()) {
                throw new IllegalArgumentException("Payment ID cannot be null or empty");
            }
            if (newStatus == null) {
                throw new IllegalArgumentException("New status cannot be null");
            }
        }
    }
}

