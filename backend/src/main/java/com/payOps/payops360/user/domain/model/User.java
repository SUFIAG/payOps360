package com.payops.payops360.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User domain model with security-first design
 * CRITICAL: All authentication/authorization MUST be server-side
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private Long organizationId; // Multi-tenant isolation

    // Identity
    private String email;
    private String passwordHash; // BCrypt, never store plain text
    private String firstName;
    private String lastName;

    // Status
    private UserStatus status;

    // Roles & Permissions
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Security
    private boolean twoFactorEnabled;
    private String twoFactorSecret; // TOTP secret
    private Set<String> backupCodes; // Recovery codes

    // Session management
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;

    // Password management
    private LocalDateTime passwordChangedAt;
    private boolean passwordExpired;
    private boolean mustChangePassword; // Force change on first login

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String invitedBy; // Who invited this user
    private LocalDateTime invitationAcceptedAt;

    // Business logic
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLocked() {
        return status == UserStatus.LOCKED ||
               (lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil));
    }

    public boolean needsPasswordChange() {
        return mustChangePassword || passwordExpired;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public boolean canAccessPaymentOps() {
        return roles.stream()
            .anyMatch(r -> r == Role.ADMIN || r == Role.OPERATIONS_MANAGER || r == Role.SUPPORT);
    }

    public boolean canModifyUsers() {
        return roles.stream()
            .anyMatch(r -> r == Role.ADMIN || r == Role.OPERATIONS_MANAGER);
    }

    public boolean canInviteUsers() {
        return isActive() && (isAdmin() || hasRole(Role.OPERATIONS_MANAGER));
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void incrementFailedLogins() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
            this.status = UserStatus.LOCKED;
        }
    }

    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }
}

