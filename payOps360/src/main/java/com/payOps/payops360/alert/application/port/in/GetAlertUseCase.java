package com.payops.payops360.alert.application.port.in;

import com.payops.payops360.alert.domain.model.Alert;

/**
 * INPUT PORT - Get alert use case
 */
public interface GetAlertUseCase {

    Alert getById(Long id);

    Alert getByAlertId(String alertId);
}

