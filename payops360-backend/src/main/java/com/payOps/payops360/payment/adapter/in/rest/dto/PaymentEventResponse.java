package com.payops.payops360.payment.adapter.in.rest.dto;

import com.payops.payops360.payment.domain.model.PaymentEvent;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for payment event (timeline entry).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventResponse {

    private PaymentStatus fromStatus;
    private PaymentStatus toStatus;
    private String reason;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private Map<String, Object> metadata;
    private Instant occurredAt;

    /**
     * Create from domain event
     */
    public static PaymentEventResponse from(PaymentEvent event) {
        return PaymentEventResponse.builder()
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .reason(event.getReason())
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .durationMs(event.getDurationMs())
                .metadata(event.getMetadata())
                .occurredAt(event.getOccurredAt())
                .build();
    }
}

