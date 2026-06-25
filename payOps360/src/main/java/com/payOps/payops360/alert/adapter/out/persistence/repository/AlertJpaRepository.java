package com.payops.payops360.alert.adapter.out.persistence.repository;

import com.payops.payops360.alert.adapter.out.persistence.entity.AlertEntity;
import com.payops.payops360.alert.domain.model.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for AlertEntity.
 */
@Repository
public interface AlertJpaRepository extends JpaRepository<AlertEntity, Long> {
    
    Optional<AlertEntity> findByAlertId(String alertId);
    
    Page<AlertEntity> findByStatus(AlertStatus status, Pageable pageable);
    
    Page<AlertEntity> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
    
    @Query("SELECT a FROM AlertEntity a WHERE a.status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING')")
    List<AlertEntity> findActiveAlerts();
    
    @Query("SELECT a FROM AlertEntity a WHERE a.status = 'OPEN' AND " +
           "EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - a.detectedAt)) > 3600")
    List<AlertEntity> findStaleAlerts();
}
