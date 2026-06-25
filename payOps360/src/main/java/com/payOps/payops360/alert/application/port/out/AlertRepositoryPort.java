package com.payops.payops360.alert.application.port.out;

import com.payops.payops360.alert.domain.model.Alert;
import com.payops.payops360.alert.domain.model.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * OUTPUT PORT - Alert repository
 */
public interface AlertRepositoryPort {

    Alert save(Alert alert);

    Optional<Alert> findById(Long id);

    Optional<Alert> findByAlertId(String alertId);

    Page<Alert> findAll(Pageable pageable);

    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    Page<Alert> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    List<Alert> findActiveAlerts();

    List<Alert> findStaleAlerts();

    void delete(Alert alert);
}

