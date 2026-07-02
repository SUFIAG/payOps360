package com.payops.payops360.payment.application.port.in;

import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentMethodType;
import com.payops.payops360.payment.domain.valueobject.Money;

import java.util.Map;

/**
 * INPUT PORT (Use Case Interface)
 *
 * Defines the contract for ingesting a new payment.
 * This is part of the hexagonal architecture's "ports" layer.
 *
 * Implementation will be in the application service layer.
 */
public interface IngestPaymentUseCase {

    /**
     * Ingest a new payment into the system
     *
     * @param command The payment ingestion command
     * @return The created payment
     */
    Payment ingest(IngestPaymentCommand command);

    /**
     * Command object for payment ingestion
     */
    record IngestPaymentCommand(
            String externalTransactionId,
            String merchantReference,
            Money amount,
            String providerId,
            String providerName,
            String providerTransactionId,
            String customerId,
            PaymentMethodType paymentMethodType,
            String paymentMethodLast4,
            Map<String, Object> metadata
    ) {
        /**
         * Validation
         */
        public IngestPaymentCommand {
            if (amount == null) {
                throw new IllegalArgumentException("Amount cannot be null");
            }
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalArgumentException("Provider ID cannot be null or empty");
            }
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalArgumentException("Customer ID cannot be null or empty");
            }
        }
    }
}

