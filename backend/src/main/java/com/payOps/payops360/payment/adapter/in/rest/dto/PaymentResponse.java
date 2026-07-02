package com.payops.payops360.payment.adapter.in.rest.dto;

import com.payops.payops360.payment.domain.model.PaymentMethodType;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for payment response.
 *
 * This is part of the OUTPUT from INPUT ADAPTER (REST layer).
 * Represents payment data sent to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String paymentId;
    private String externalTransactionId;
    private String merchantReference;

    private BigDecimal amount;
    private String currency;

    private String providerId;
    private String providerName;
    private String providerTransactionId;

    private String customerId;
    private PaymentMethodType paymentMethodType;
    private String paymentMethodLast4;

    private PaymentStatus status;
    private PaymentStatus previousStatus;
    private Instant statusChangedAt;

    private int retryCount;
    private boolean isStuck;
    private Instant stuckDetectedAt;

    private Map<String, Object> metadata;
    private List<String> tags;

    private Instant createdAt;
    private Instant updatedAt;
}

