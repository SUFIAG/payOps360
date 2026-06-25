package com.payOps.payops360.failure.adapter.output.persistence.entity;

import com.payOps.payops360.failure.domain.model.FailureSeverity;
import com.payOps.payops360.failure.domain.model.FailureType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Entity for Failure Classification
 */
@Entity
@Table(name = "failure_classifications", indexes = {
        @Index(name = "idx_failure_payment_id", columnList = "payment_id"),
        @Index(name = "idx_failure_type", columnList = "failure_type"),
        @Index(name = "idx_failure_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureClassificationEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 50)
    private FailureType failureType;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private FailureSeverity severity;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "is_retryable", nullable = false)
    private Boolean retryable;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "classified_at", nullable = false)
    private Instant classifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

