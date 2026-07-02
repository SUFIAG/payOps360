package com.payops.payops360.alert.application.port.in;

import com.payops.payops360.alert.domain.model.Alert;

/**
 * INPUT PORT - Update alert use case
 */
public interface UpdateAlertUseCase {

    Alert acknowledge(String alertId, String userId);

    Alert resolve(ResolveAlertCommand command);

    Alert suppress(SuppressAlertCommand command);

    record ResolveAlertCommand(
            String alertId,
            String userId,
            String resolutionNote
    ) {}

    record SuppressAlertCommand(
            String alertId,
            String userId,
            String reason
    ) {}
}

