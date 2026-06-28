package com.payops.payops360.payment.adapter.in.rest.dto;

import com.payops.payops360.payment.domain.model.PaymentMethodType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for payment ingestion request.
 *
 * This is part of the INPUT ADAPTER (REST layer).
 * Validates incoming requests before passing to application layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestPaymentRequest {

    @NotBlank(message = "External transaction ID is required")
    private String externalTransactionId;

    private String merchantReference;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @NotBlank(message = "Provider ID is required")
    private String providerId;

    private String providerName;

    private String providerTransactionId;

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType paymentMethodType;

    private String paymentMethodLast4;

    private Map<String, Object> metadata;
}

