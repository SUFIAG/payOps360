package com.payOps.payops360.incident.adapter.output.persistence.entity;

import com.payOps.payops360.incident.domain.model.IncidentCategory;
import com.payOps.payops360.incident.domain.model.IncidentSeverity;
import com.payOps.payops360.incident.domain.model.IncidentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for Incident
 */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_provider", columnList = "affected_provider_id"),
        @Index(name = "idx_incident_severity", columnList = "severity"),
        @Index(name = "idx_incident_detected_at", columnList = "detected_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private IncidentCategory category;

    @Column(name = "affected_provider_id", length = 100)
    private String affectedProviderId;

    @Column(name = "affected_region", length = 100)
    private String affectedRegion;

    @Column(name = "impacted_payment_count", nullable = false)
    private Integer impactedPaymentCount;

    @Column(name = "estimated_impact", nullable = false)
    private Double estimatedImpact;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_alert_ids", columnDefinition = "jsonb")
    private List<UUID> relatedAlertIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_payment_ids", columnDefinition = "jsonb")
    private List<UUID> relatedPaymentIds;
}

