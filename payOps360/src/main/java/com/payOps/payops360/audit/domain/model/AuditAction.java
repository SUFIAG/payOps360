package com.payops.payops360.audit.domain.model;

/**
 * Types of actions that can be audited.
 */
public enum AuditAction {
    // Payment actions
    PAYMENT_CREATED,
    PAYMENT_STATUS_UPDATED,
    PAYMENT_RETRY_INITIATED,

    // Provider actions
    PROVIDER_REGISTERED,
    PROVIDER_ACTIVATED,
    PROVIDER_DEACTIVATED,
    PROVIDER_HEALTH_UPDATED,

    // Alert actions
    ALERT_CREATED,
    ALERT_ACKNOWLEDGED,
    ALERT_RESOLVED,

    // System actions
    USER_LOGIN,
    USER_LOGOUT,
    CONFIG_UPDATED,

    // Generic
    ENTITY_CREATED,
    ENTITY_UPDATED,
    ENTITY_DELETED
}

