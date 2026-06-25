package com.payOps.payops360.retry.adapter.output.persistence.repository;

import com.payOps.payops360.retry.adapter.output.persistence.entity.RetryStrategyEntity;
import com.payOps.payops360.retry.domain.model.RetryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA Repository for Retry Strategies
 */
@Repository
public interface RetryStrategyJpaRepository extends JpaRepository<RetryStrategyEntity, UUID> {

    @Query("""
            SELECT r FROM RetryStrategyEntity r
            WHERE (:paymentId IS NULL OR r.paymentId = :paymentId)
            AND (:status IS NULL OR r.status = :status)
            AND (:failureCategory IS NULL OR r.failureCategory = :failureCategory)
            ORDER BY r.recommendedAt DESC
            """)
    Page<RetryStrategyEntity> findAllWithFilters(
            @Param("paymentId") UUID paymentId,
            @Param("status") RetryStatus status,
            @Param("failureCategory") String failureCategory,
            Pageable pageable
    );
}

