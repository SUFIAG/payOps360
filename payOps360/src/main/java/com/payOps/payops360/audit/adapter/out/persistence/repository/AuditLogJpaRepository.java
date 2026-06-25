package com.payops.payops360.audit.adapter.out.persistence.repository;

import com.payops.payops360.audit.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for AuditLogEntity.
 */
@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {
    // Query methods can be added later as needed
}

