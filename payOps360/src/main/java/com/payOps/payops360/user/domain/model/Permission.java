package com.payOps.payops360.user.domain.model;

/**
 * Granular permissions for fine-grained access control
 */
public enum Permission {
    // Super permission
    ALL,

    // Payment operations
    READ_PAYMENTS,
    WRITE_PAYMENTS,
    DELETE_PAYMENTS,

    // Provider management
    READ_PROVIDERS,
    WRITE_PROVIDERS,
    DELETE_PROVIDERS,

    // Alerts
    READ_ALERTS,
    WRITE_ALERTS,
    RESOLVE_ALERTS,

    // Incidents
    READ_INCIDENTS,
    WRITE_INCIDENTS,
    RESOLVE_INCIDENTS,
    ESCALATE_INCIDENTS,

    // Analytics
    READ_ANALYTICS,
    EXPORT_REPORTS,

    // User management
    READ_USERS,
    WRITE_USERS,
    DELETE_USERS,
    INVITE_USERS,
    MANAGE_ROLES,

    // Organization management
    READ_ORGANIZATION,
    WRITE_ORGANIZATION,
    MANAGE_BILLING,

    // AI features
    USE_AI_INVESTIGATION,
    TRAIN_AI_MODELS,

    // API access
    API_ACCESS,

    // System administration
    MANAGE_SYSTEM_SETTINGS,
    VIEW_AUDIT_LOGS,
    MANAGE_INTEGRATIONS
}

