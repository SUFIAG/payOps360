package com.payops.payops360.alert.application.service;

import com.payops.payops360.alert.application.port.in.*;
import com.payops.payops360.alert.application.port.out.AlertRepositoryPort;
import com.payops.payops360.alert.domain.model.Alert;
import com.payops.payops360.alert.domain.model.AlertStatus;
import com.payops.payops360.alert.domain.service.AlertDetectionService;
import com.payops.payops360.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Alert Application Service
 * 
 * Implements all alert use cases.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlertService implements 
        CreateAlertUseCase,
        GetAlertUseCase,
        ListAlertsUseCase,
        UpdateAlertUseCase {
    
    private final AlertRepositoryPort alertRepository;
    private final AlertDetectionService detectionService;
    
    @Override
    public Alert create(CreateAlertCommand command) {
        log.info("Creating alert: type={}, entity={}:{}", 
                command.alertType(), command.entityType(), command.entityId());
        
        Alert alert = Alert.builder()
                .alertId(generateAlertId())
                .alertType(command.alertType())
                .severity(command.severity())
                .entityType(command.entityType())
                .entityId(command.entityId())
                .title(command.title())
                .description(command.description())
                .metricName(command.metricName())
                .metricValue(command.metricValue())
                .thresholdValue(command.thresholdValue())
                .status(AlertStatus.OPEN)
                .detectedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .statusChangedAt(Instant.now())
                .build();
        
        Alert saved = alertRepository.save(alert);
        log.info("Alert created successfully: alertId={}", saved.getAlertId());
        
        return saved;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Alert getById(Long id) {
        log.debug("Fetching alert by ID: {}", id);
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id.toString()));
    }
    
    @Override
    @Transactional(readOnly = true)
    public Alert getByAlertId(String alertId) {
        log.debug("Fetching alert by alertId: {}", alertId);
        return alertRepository.findByAlertId(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> listAll(Pageable pageable) {
        log.debug("Listing all alerts");
        return alertRepository.findAll(pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> listByStatus(AlertStatus status, Pageable pageable) {
        log.debug("Listing alerts by status: {}", status);
        return alertRepository.findByStatus(status, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> listByEntity(String entityType, String entityId, Pageable pageable) {
        log.debug("Listing alerts for entity: {}:{}", entityType, entityId);
        return alertRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> listActive(Pageable pageable) {
        log.debug("Listing active alerts");
        return alertRepository.findByStatus(AlertStatus.OPEN, pageable);
    }
    
    @Override
    public Alert acknowledge(String alertId, String userId) {
        log.info("Acknowledging alert: alertId={}, user={}", alertId, userId);
        
        Alert alert = getByAlertId(alertId);
        alert.acknowledge(userId);
        
        Alert updated = alertRepository.save(alert);
        log.info("Alert acknowledged: alertId={}", alertId);
        
        return updated;
    }
    
    @Override
    public Alert resolve(ResolveAlertCommand command) {
        log.info("Resolving alert: alertId={}", command.alertId());
        
        Alert alert = getByAlertId(command.alertId());
        alert.resolve(command.userId(), command.resolutionNote());
        
        Alert updated = alertRepository.save(alert);
        log.info("Alert resolved: alertId={}", command.alertId());
        
        return updated;
    }
    
    @Override
    public Alert suppress(SuppressAlertCommand command) {
        log.info("Suppressing alert: alertId={}", command.alertId());
        
        Alert alert = getByAlertId(command.alertId());
        alert.suppress(command.userId(), command.reason());
        
        Alert updated = alertRepository.save(alert);
        log.info("Alert suppressed: alertId={}", command.alertId());
        
        return updated;
    }
    
    private String generateAlertId() {
        return "ALT-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
