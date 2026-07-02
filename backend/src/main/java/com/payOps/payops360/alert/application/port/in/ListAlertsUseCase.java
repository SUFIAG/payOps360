package com.payops.payops360.alert.application.port.in;

import com.payops.payops360.alert.domain.model.Alert;
import com.payops.payops360.alert.domain.model.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * INPUT PORT - List alerts use case
 */
public interface ListAlertsUseCase {

    Page<Alert> listAll(Pageable pageable);

    Page<Alert> listByStatus(AlertStatus status, Pageable pageable);

    Page<Alert> listByEntity(String entityType, String entityId, Pageable pageable);

    Page<Alert> listActive(Pageable pageable);
}

