package com.payOps.payops360.ai.adapter.output.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for AI Investigation
 */
@Entity
@Table(name = "ai_investigations", indexes = {
        @Index(name = "idx_ai_investigation_incident_id", columnList = "incident_id"),
        @Index(name = "idx_ai_investigation_investigated_at", columnList = "investigated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIInvestigationEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private UUID incidentId;

    @Column(name = "investigation_type", nullable = false, length = 50)
    private String investigationType;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "investigated_at", nullable = false)
    private Instant investigatedAt;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
}

