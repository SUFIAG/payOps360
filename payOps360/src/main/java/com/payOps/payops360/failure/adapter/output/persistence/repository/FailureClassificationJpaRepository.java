package com.payOps.payops360.failure.adapter.output.persistence.repository;

import com.payOps.payops360.failure.adapter.output.persistence.entity.FailureClassificationEntity;
import com.payOps.payops360.failure.domain.model.FailureType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA Repository for Failure Classifications
 */
@Repository
public interface FailureClassificationJpaRepository extends JpaRepository<FailureClassificationEntity, UUID> {

    @Query("""
            SELECT f FROM FailureClassificationEntity f
            WHERE (:paymentId IS NULL OR f.paymentId = :paymentId)
            AND (:failureType IS NULL OR f.failureType = :failureType)
            AND (:category IS NULL OR f.category = :category)
            ORDER BY f.classifiedAt DESC
            """)
    Page<FailureClassificationEntity> findAllWithFilters(
            @Param("paymentId") UUID paymentId,
            @Param("failureType") FailureType failureType,
            @Param("category") String category,
            Pageable pageable
    );
}

