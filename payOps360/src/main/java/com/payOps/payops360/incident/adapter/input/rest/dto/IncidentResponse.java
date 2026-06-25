package com.payOps.payops360.incident.adapter.input.rest.dto;

import com.payOps.payops360.incident.domain.model.IncidentCategory;
import com.payOps.payops360.incident.domain.model.IncidentSeverity;
import com.payOps.payops360.incident.domain.model.IncidentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Incident
 */
public record IncidentResponse(
        UUID id,
        String title,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        IncidentCategory category,
        String affectedProviderId,
        String affectedRegion,
        int impactedPaymentCount,
        double estimatedImpact,
        Instant detectedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String assignedTo,
        String rootCause,
        String resolution,
        List<UUID> relatedAlertIds,
        List<UUID> relatedPaymentIds,
        Long durationMinutes
) {}

