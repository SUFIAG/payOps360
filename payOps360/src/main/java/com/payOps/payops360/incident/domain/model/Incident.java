package com.payOps.payops360.incident.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Incident Domain Model (Pure Domain - No Framework Dependencies)
 * Represents a correlated group of related alerts/failures
 */
@Getter
@Builder
public class Incident {
    private final UUID id;
    private final String title;
    private final String description;
    private final IncidentSeverity severity;
    private final IncidentStatus status;
    private final IncidentCategory category;
    private final String affectedProviderId;
    private final String affectedRegion;
    private final int impactedPaymentCount;
    private final double estimatedImpact;
    private final Instant detectedAt;
    private final Instant acknowledgedAt;
    private final Instant resolvedAt;
    private final String assignedTo;
    private final String rootCause;
    private final String resolution;
    @Builder.Default
    private final List<UUID> relatedAlertIds = new ArrayList<>();
    @Builder.Default
    private final List<UUID> relatedPaymentIds = new ArrayList<>();

    public Incident acknowledge(String assignedTo) {
        if (this.status != IncidentStatus.OPEN) {
            throw new IllegalStateException("Can only acknowledge OPEN incidents");
        }

        return Incident.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .severity(this.severity)
                .status(IncidentStatus.INVESTIGATING)
                .category(this.category)
                .affectedProviderId(this.affectedProviderId)
                .affectedRegion(this.affectedRegion)
                .impactedPaymentCount(this.impactedPaymentCount)
                .estimatedImpact(this.estimatedImpact)
                .detectedAt(this.detectedAt)
                .acknowledgedAt(Instant.now())
                .resolvedAt(this.resolvedAt)
                .assignedTo(assignedTo)
                .rootCause(this.rootCause)
                .resolution(this.resolution)
                .relatedAlertIds(this.relatedAlertIds)
                .relatedPaymentIds(this.relatedPaymentIds)
                .build();
    }

    public Incident resolve(String rootCause, String resolution) {
        if (this.status == IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Incident already resolved");
        }

        return Incident.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .severity(this.severity)
                .status(IncidentStatus.RESOLVED)
                .category(this.category)
                .affectedProviderId(this.affectedProviderId)
                .affectedRegion(this.affectedRegion)
                .impactedPaymentCount(this.impactedPaymentCount)
                .estimatedImpact(this.estimatedImpact)
                .detectedAt(this.detectedAt)
                .acknowledgedAt(this.acknowledgedAt)
                .resolvedAt(Instant.now())
                .assignedTo(this.assignedTo)
                .rootCause(rootCause)
                .resolution(resolution)
                .relatedAlertIds(this.relatedAlertIds)
                .relatedPaymentIds(this.relatedPaymentIds)
                .build();
    }

    public Incident escalate() {
        IncidentSeverity newSeverity = switch (this.severity) {
            case LOW -> IncidentSeverity.MEDIUM;
            case MEDIUM -> IncidentSeverity.HIGH;
            case HIGH, CRITICAL -> IncidentSeverity.CRITICAL;
        };

        return Incident.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .severity(newSeverity)
                .status(this.status)
                .category(this.category)
                .affectedProviderId(this.affectedProviderId)
                .affectedRegion(this.affectedRegion)
                .impactedPaymentCount(this.impactedPaymentCount)
                .estimatedImpact(this.estimatedImpact)
                .detectedAt(this.detectedAt)
                .acknowledgedAt(this.acknowledgedAt)
                .resolvedAt(this.resolvedAt)
                .assignedTo(this.assignedTo)
                .rootCause(this.rootCause)
                .resolution(this.resolution)
                .relatedAlertIds(this.relatedAlertIds)
                .relatedPaymentIds(this.relatedPaymentIds)
                .build();
    }

    public long getDurationMinutes() {
        Instant endTime = resolvedAt != null ? resolvedAt : Instant.now();
        return (endTime.toEpochMilli() - detectedAt.toEpochMilli()) / 60000;
    }

    public boolean isActive() {
        return status != IncidentStatus.RESOLVED;
    }
}

