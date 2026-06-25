package com.payOps.payops360.ai.adapter.output.persistence.repository;

import com.payOps.payops360.ai.adapter.output.persistence.entity.AIInvestigationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for AI Investigations
 */
@Repository
public interface AIInvestigationJpaRepository extends JpaRepository<AIInvestigationEntity, UUID> {
    Optional<AIInvestigationEntity> findByIncidentId(UUID incidentId);
}

