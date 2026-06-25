package com.payOps.payops360.retry.adapter.output.persistence.entity;

import com.payOps.payops360.retry.domain.model.RetryStatus;
import com.payOps.payops360.retry.domain.model.RetryStrategyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for Retry Strategy
 */
@Entity
@Table(name = "retry_strategies", indexes = {
        @Index(name = "idx_retry_payment_id", columnList = "payment_id"),
        @Index(name = "idx_retry_status", columnList = "status"),
        @Index(name = "idx_retry_failure_category", columnList = "failure_category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryStrategyEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "failure_category", nullable = false, length = 50)
    private String failureCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 50)
    private RetryStrategyType strategyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RetryStatus status;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "delay_millis", nullable = false)
    private Long delayMillis;

    @Column(name = "fallback_provider_id", length = 100)
    private String fallbackProviderId;

    @Column(name = "recommendation", nullable = false, length = 500)
    private String recommendation;

    @Column(name = "reasoning", nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "recommended_at", nullable = false)
    private Instant recommendedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "current_attempt")
    private Integer currentAttempt;

    @Column(name = "successful", nullable = false)
    private Boolean successful;
}

