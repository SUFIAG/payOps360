package com.payops.payops360.alert.adapter.out.persistence;

import com.payops.payops360.alert.adapter.out.persistence.entity.AlertEntity;
import com.payops.payops360.alert.adapter.out.persistence.mapper.AlertPersistenceMapper;
import com.payops.payops360.alert.adapter.out.persistence.repository.AlertJpaRepository;
import com.payops.payops360.alert.application.port.out.AlertRepositoryPort;
import com.payops.payops360.alert.domain.model.Alert;
import com.payops.payops360.alert.domain.model.AlertStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Alert Persistence Adapter - OUTPUT ADAPTER
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertPersistenceAdapter implements AlertRepositoryPort {
    
    private final AlertJpaRepository jpaRepository;
    private final AlertPersistenceMapper mapper;
    
    @Override
    @Transactional
    public Alert save(Alert alert) {
        log.debug("Saving alert: alertId={}", alert.getAlertId());
        
        AlertEntity entity;
        
        if (alert.getId() != null) {
            entity = jpaRepository.findById(alert.getId())
                    .orElseThrow(() -> new IllegalStateException("Alert not found: " + alert.getId()));
            mapper.updateEntity(entity, alert);
        } else {
            entity = mapper.toEntity(alert);
        }
        
        AlertEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Alert> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Alert> findByAlertId(String alertId) {
        return jpaRepository.findByAlertId(alertId).map(mapper::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> findByStatus(AlertStatus status, Pageable pageable) {
        return jpaRepository.findByStatus(status, pageable).map(mapper::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Alert> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable) {
        return jpaRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable)
                .map(mapper::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Alert> findActiveAlerts() {
        return jpaRepository.findActiveAlerts().stream()
                .map(mapper::toDomain)
                .toList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Alert> findStaleAlerts() {
        return jpaRepository.findStaleAlerts().stream()
                .map(mapper::toDomain)
                .toList();
    }
    
    @Override
    @Transactional
    public void delete(Alert alert) {
        if (alert.getId() != null) {
            jpaRepository.deleteById(alert.getId());
        }
    }
}
