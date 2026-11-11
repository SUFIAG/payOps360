package com.payops.alert.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "alerts")
class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;
    @Column(nullable = false, updatable = false)
    private String reference;
    @Column(nullable = false, updatable = false)
    private String type;
    private String severity;
    private String status;
    @Column(nullable = false, updatable = false)
    private String source;
    @Column(nullable = false, updatable = false)
    private String title;
    private String description;
    @Column(name = "provider_id", updatable = false)
    private Long providerId;
    @Column(name = "payment_reference", updatable = false)
    private String paymentReference;
    @Column(name = "incident_id")
    private Long incidentId;
    @Column(name = "metric_value")
    private BigDecimal metricValue;
    @Column(updatable = false)
    private BigDecimal threshold;
    @Column(nullable = false, updatable = false)
    private String fingerprint;
    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "acknowledged_by")
    private String acknowledgedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolved_by")
    private String resolvedBy;
    @Column(name = "resolution_note")
    private String resolutionNote;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
}
