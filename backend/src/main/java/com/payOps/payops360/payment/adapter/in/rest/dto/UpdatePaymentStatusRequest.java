package com.payops.payops360.payment.adapter.in.rest.dto;

import com.payops.payops360.payment.domain.model.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating payment status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentStatusRequest {

    @NotNull(message = "New status is required")
    private PaymentStatus newStatus;

    private String reason;
    private String errorCode;
    private String errorMessage;
}

