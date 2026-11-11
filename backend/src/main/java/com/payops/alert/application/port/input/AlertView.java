package com.payops.alert.application.port.input;

import com.payops.alert.domain.Alert;
import com.payops.alert.domain.AlertSource;
import com.payops.alert.domain.AlertStatus;
import com.payops.alert.domain.AlertType;
import com.payops.shared.domain.Severity;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertView(String reference, AlertType type, Severity severity, AlertStatus status, AlertSource source,
                        String title, String description, String providerCode, String paymentReference,
                        String incidentReference, BigDecimal metricValue, BigDecimal threshold, Instant detectedAt,
                        Instant lastSeenAt, Instant acknowledgedAt, String acknowledgedBy, Instant resolvedAt,
                        String resolvedBy, String resolutionNote) {

    public static AlertView of(Alert a, String providerCode, String incidentReference) {
        return new AlertView(a.reference(), a.type(), a.severity(), a.status(), a.source(), a.title(), a.description(),
                providerCode, a.paymentReference(), incidentReference, a.metricValue(), a.threshold(), a.detectedAt(),
                a.lastSeenAt(), a.acknowledgedAt(), a.acknowledgedBy(), a.resolvedAt(), a.resolvedBy(),
                a.resolutionNote());
    }
}
