package com.payops.incident.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "incidents")
class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;
    @Column(nullable = false, updatable = false)
    private String reference;
    @Column(nullable = false, updatable = false)
    private String title;
    private String description;
    private String severity;
    private String status;
    @Column(name = "provider_id", updatable = false)
    private Long providerId;
    @Column(name = "affected_payments")
    private int affectedPayments;
    @Column(name = "affected_amount")
    private BigDecimal affectedAmount;
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;
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
    @Column(name = "ai_summary")
    private String aiSummary;
    @Column(name = "ai_root_cause")
    private String aiRootCause;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_recommendations", columnDefinition = "jsonb")
    private List<String> aiRecommendations;
    @Column(name = "ai_confidence")
    private BigDecimal aiConfidence;
    @Column(name = "ai_engine")
    private String aiEngine;
    @Column(name = "ai_generated_at")
    private Instant aiGeneratedAt;
    @Column(name = "ack_due_at")
    private Instant ackDueAt;
    @Column(name = "resolve_due_at")
    private Instant resolveDueAt;
    @Column(name = "ack_breached_at")
    private Instant ackBreachedAt;
    @Column(name = "resolve_breached_at")
    private Instant resolveBreachedAt;
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
}
