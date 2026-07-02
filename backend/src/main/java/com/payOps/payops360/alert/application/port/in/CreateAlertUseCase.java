package com.payops.payops360.alert.application.port.in;

import com.payops.payops360.alert.domain.model.Alert;

/**
 * INPUT PORT - Create alert use case
 */
public interface CreateAlertUseCase {

    Alert create(CreateAlertCommand command);

    record CreateAlertCommand(
            com.payops.payops360.alert.domain.model.AlertType alertType,
            com.payops.payops360.alert.domain.model.AlertSeverity severity,
            String entityType,
            String entityId,
            String title,
            String description,
            String metricName,
            Double metricValue,
            Double thresholdValue
    ) {
        public CreateAlertCommand {
            if (alertType == null) {
                throw new IllegalArgumentException("Alert type cannot be null");
            }
            if (severity == null) {
                throw new IllegalArgumentException("Severity cannot be null");
            }
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalArgumentException("Entity type cannot be null or empty");
            }
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalArgumentException("Entity ID cannot be null or empty");
            }
        }
    }
}

