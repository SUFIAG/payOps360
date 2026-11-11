package com.payops.alert.application.port.in;

import com.payops.alert.domain.AlertSource;
import com.payops.alert.domain.AlertType;
import com.payops.shared.domain.Severity;

import java.math.BigDecimal;

/**
 * Entry point for detectors (rules, anomaly models, payment signals).
 */
public interface RaiseAlertUseCase {

    /**
     * Raises an alert, or refreshes the open alert with the same fingerprint.
     */
    AlertView raise(RaiseAlertCommand command);

    /** Resolves open rule/AI alerts whose condition has not recurred recently. */
    int autoResolveQuietAlerts();

    record RaiseAlertCommand(long organizationId, AlertType type, Severity severity, AlertSource source, String title,
                             String description, Long providerId, String paymentReference, BigDecimal metricValue,
                             BigDecimal threshold, String fingerprint) {
    }
}
