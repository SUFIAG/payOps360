package com.payops.payops360.alert.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Alert Domain Model - Pure business entity.
 *
 * Represents an alert triggered by system conditions.
 * NO framework dependencies - pure domain logic.
 */
@Getter
@Builder(toBuilder = true)
public class Alert {

    // Identity
    private final Long id;
    private final String alertId;  // Business identifier (e.g., "ALT-12345")

    // Classification
    private final AlertType alertType;
    private final AlertSeverity severity;

    // Target Entity
    private final String entityType;  // "PROVIDER", "PAYMENT", "SYSTEM"
    private final String entityId;    // Business ID of the entity

    // Details
    private final String title;
    private final String description;
    private final String metricName;
    private final Double metricValue;
    private final Double thresholdValue;

    // Status
    private AlertStatus status;
    private Instant statusChangedAt;

    // Resolution
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNote;

    // Incident Link (for correlation)
    private Long incidentId;

    // Metadata
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    // Timestamps
    private final Instant detectedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Acknowledge the alert
     */
    public void acknowledge(String userId) {
        if (this.status == AlertStatus.OPEN) {
            this.status = AlertStatus.ACKNOWLEDGED;
            this.statusChangedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Mark alert as investigating
     */
    public void startInvestigating() {
        if (this.status == AlertStatus.ACKNOWLEDGED || this.status == AlertStatus.OPEN) {
            this.status = AlertStatus.INVESTIGATING;
            this.statusChangedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Resolve the alert manually
     */
    public void resolve(String userId, String note) {
        if (!this.status.isTerminal()) {
            this.status = AlertStatus.RESOLVED;
            this.resolvedAt = Instant.now();
            this.resolvedBy = userId;
            this.resolutionNote = note;
            this.statusChangedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Auto-resolve when condition clears
     */
    public void autoResolve(String reason) {
        if (!this.status.isTerminal()) {
            this.status = AlertStatus.AUTO_RESOLVED;
            this.resolvedAt = Instant.now();
            this.resolvedBy = "SYSTEM";
            this.resolutionNote = reason;
            this.statusChangedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Suppress the alert
     */
    public void suppress(String userId, String reason) {
        if (!this.status.isTerminal()) {
            this.status = AlertStatus.SUPPRESSED;
            this.resolvedAt = Instant.now();
            this.resolvedBy = userId;
            this.resolutionNote = "Suppressed: " + reason;
            this.statusChangedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Link alert to an incident
     */
    public void linkToIncident(Long incidentId) {
        this.incidentId = incidentId;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if alert is resolved
     */
    public boolean isResolved() {
        return status.isTerminal();
    }

    /**
     * Check if alert is active
     */
    public boolean isActive() {
        return status.isActive();
    }

    /**
     * Check if alert requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return severity.requiresImmediateAttention() && isActive();
    }

    /**
     * Get alert age in seconds
     */
    public long getAgeInSeconds() {
        return Instant.now().getEpochSecond() - detectedAt.getEpochSecond();
    }

    /**
     * Check if alert has been open too long (more than 1 hour without acknowledgment)
     */
    public boolean isStale() {
        return status == AlertStatus.OPEN && getAgeInSeconds() > 3600;
    }
}

