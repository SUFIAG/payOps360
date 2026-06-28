package com.payOps.payops360.user.domain.model;

import lombok.Getter;

import java.util.Set;

/**
 * Role-based access control (RBAC)
 * Each role has specific permissions
 */
@Getter
public enum Role {
    // Super admin - full system access
    ADMIN(Set.of(
        Permission.ALL
    )),

    // Operations manager - manage payments, providers, incidents
    OPERATIONS_MANAGER(Set.of(
        Permission.READ_PAYMENTS,
        Permission.WRITE_PAYMENTS,
        Permission.READ_PROVIDERS,
        Permission.WRITE_PROVIDERS,
        Permission.READ_ALERTS,
        Permission.WRITE_ALERTS,
        Permission.READ_INCIDENTS,
        Permission.WRITE_INCIDENTS,
        Permission.READ_ANALYTICS,
        Permission.INVITE_USERS,
        Permission.READ_USERS,
        Permission.USE_AI_INVESTIGATION
    )),

    // Analyst - read-only access to analytics and reports
    ANALYST(Set.of(
        Permission.READ_PAYMENTS,
        Permission.READ_PROVIDERS,
        Permission.READ_ALERTS,
        Permission.READ_INCIDENTS,
        Permission.READ_ANALYTICS,
        Permission.USE_AI_INVESTIGATION
    )),

    // Support - view payments, create tickets
    SUPPORT(Set.of(
        Permission.READ_PAYMENTS,
        Permission.READ_PROVIDERS,
        Permission.READ_ALERTS,
        Permission.WRITE_ALERTS
    )),

    // API user - programmatic access only
    API_USER(Set.of(
        Permission.API_ACCESS
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(Permission.ALL) || permissions.contains(permission);
    }
}

