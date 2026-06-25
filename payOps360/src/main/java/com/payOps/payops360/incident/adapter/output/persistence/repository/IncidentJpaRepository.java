package com.payOps.payops360.incident.adapter.output.persistence.repository;

import com.payOps.payops360.incident.adapter.output.persistence.entity.IncidentEntity;
import com.payOps.payops360.incident.domain.model.IncidentSeverity;
import com.payOps.payops360.incident.domain.model.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA Repository for Incidents
 */
@Repository
public interface IncidentJpaRepository extends JpaRepository<IncidentEntity, UUID> {

    @Query("""
            SELECT i FROM IncidentEntity i
            WHERE (:status IS NULL OR i.status = :status)
            AND (:providerId IS NULL OR i.affectedProviderId = :providerId)
            AND (:severity IS NULL OR i.severity = :severity)
            ORDER BY i.detectedAt DESC
            """)
    Page<IncidentEntity> findAllWithFilters(
            @Param("status") IncidentStatus status,
            @Param("providerId") String providerId,
            @Param("severity") IncidentSeverity severity,
            Pageable pageable
    );
}

