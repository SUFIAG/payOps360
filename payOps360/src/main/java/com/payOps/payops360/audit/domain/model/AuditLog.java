package com.payops.payops360.audit.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * AuditLog Domain Model - Pure business entity.
 *
 * Records all significant actions in the system.
 * NO framework dependencies.
 */
@Getter
@Builder(toBuilder = true)
public class AuditLog {

    private final Long id;

    // Action Details
    private final AuditAction action;
    private final String entityType;    // e.g., "Payment", "Provider"
    private final String entityId;      // Business ID of the entity

    // Actor Information
    private final String userId;
    private final String ipAddress;
    private final String userAgent;

    // Change Details
    @Builder.Default
    private final Map<String, Object> oldValues = Map.of();
    @Builder.Default
    private final Map<String, Object> newValues = Map.of();

    // Context
    private final String requestId;
    private final String sessionId;

    // Timestamp
    private final Instant occurredAt;

    /**
     * Check if this audit log represents a creation action
     */
    public boolean isCreation() {
        return action == AuditAction.ENTITY_CREATED ||
               action == AuditAction.PAYMENT_CREATED ||
               action == AuditAction.PROVIDER_REGISTERED ||
               action == AuditAction.ALERT_CREATED;
    }

    /**
     * Check if this audit log represents an update action
     */
    public boolean isUpdate() {
        return action == AuditAction.ENTITY_UPDATED ||
               action == AuditAction.PAYMENT_STATUS_UPDATED ||
               action == AuditAction.PROVIDER_HEALTH_UPDATED;
    }

    /**
     * Check if this audit log represents a deletion action
     */
    public boolean isDeletion() {
        return action == AuditAction.ENTITY_DELETED;
    }

    /**
     * Get a human-readable description of the action
     */
    public String getDescription() {
        return String.format("%s performed %s on %s %s",
                userId != null ? userId : "System",
                action.name(),
                entityType,
                entityId);
    }
}

