package com.payops.identity.domain;

import java.util.EnumSet;
import java.util.Set;

import static com.payops.identity.domain.Permission.*;

/**
 * Least-privilege role catalogue. Segregation of duties: only ADMIN manages
 * users, AUDITOR has read-only access plus the audit trail.
 */
public enum Role {
    ADMIN(EnumSet.allOf(Permission.class)),
    OPERATIONS_MANAGER(EnumSet.of(PAYMENT_READ, PAYMENT_WRITE, PROVIDER_READ, PROVIDER_MANAGE, ALERT_READ,
            ALERT_MANAGE, INCIDENT_READ, INCIDENT_MANAGE, ANALYTICS_READ, AI_USE, AUDIT_READ, NOTIFICATION_MANAGE,
            RECON_READ, RECON_MANAGE)),
    ANALYST(EnumSet.of(PAYMENT_READ, PROVIDER_READ, ALERT_READ, INCIDENT_READ, ANALYTICS_READ, AI_USE, RECON_READ)),
    SUPPORT(EnumSet.of(PAYMENT_READ, PROVIDER_READ, ALERT_READ, ALERT_MANAGE, INCIDENT_READ, AI_USE)),
    AUDITOR(EnumSet.of(PAYMENT_READ, PROVIDER_READ, ALERT_READ, INCIDENT_READ, ANALYTICS_READ, AUDIT_READ, RECON_READ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return EnumSet.copyOf(permissions);
    }
}
