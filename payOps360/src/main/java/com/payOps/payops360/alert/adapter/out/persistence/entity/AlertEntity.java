package com.payops.payops360.alert.adapter.out.persistence.entity;

import com.payops.payops360.alert.domain.model.AlertSeverity;
import com.payops.payops360.alert.domain.model.AlertStatus;
import com.payops.payops360.alert.domain.model.AlertType;
import com.payops.payops360.common.util.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/**
 * Alert JPA Entity - OUTPUT ADAPTER
 */
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_alert_id", columnList = "alert_id"),
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_alert_detected", columnList = "detected_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEntity extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "alert_id", unique = true, nullable = false)
    private String alertId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;
    
    @Column(name = "entity_type")
    private String entityType;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "metric_name")
    private String metricName;
    
    @Column(name = "metric_value")
    private Double metricValue;
    
    @Column(name = "threshold_value")
    private Double thresholdValue;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertStatus status;
    
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;
    
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    
    @Column(name = "resolved_by")
    private String resolvedBy;
    
    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;
    
    @Column(name = "incident_id")
    private Long incidentId;
    
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
}
