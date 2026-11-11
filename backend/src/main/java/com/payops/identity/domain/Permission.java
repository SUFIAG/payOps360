package com.payops.identity.domain;

/**
 * Fine-grained capabilities. Endpoints authorize against permissions, never
 * against role names, so roles can evolve without touching controllers.
 */
public enum Permission {
    PAYMENT_READ,
    PAYMENT_WRITE,
    PROVIDER_READ,
    PROVIDER_MANAGE,
    ALERT_READ,
    ALERT_MANAGE,
    INCIDENT_READ,
    INCIDENT_MANAGE,
    ANALYTICS_READ,
    AI_USE,
    USER_MANAGE,
    AUDIT_READ,
    /** Configure notification channels, routing rules, escalation policies and SLA targets. */
    NOTIFICATION_MANAGE,
    /** View settlement batches, reconciliation exceptions, fee schedules and reconciliation KPIs. */
    RECON_READ,
    /** Import settlement files, work the exception queue, edit fee schedules and column mappings. */
    RECON_MANAGE
}
