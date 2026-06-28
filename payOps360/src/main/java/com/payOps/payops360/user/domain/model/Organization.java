package com.payOps.payops360.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Organization domain model for multi-tenancy
 * Each organization has isolated data access
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {
    private Long id;
    private String name;
    private String domain; // e.g., "acme-corp" for acme-corp.payops360.com
    private OrganizationStatus status;
    private OrganizationTier tier;

    // Settings
    private OrganizationSettings settings;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    // Business logic
    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == OrganizationStatus.SUSPENDED;
    }

    public boolean canInviteUsers() {
        return isActive() && settings != null && !settings.isUserInvitationDisabled();
    }

    public boolean requires2FA() {
        return settings != null && settings.isForce2FA();
    }

    public int getMaxUsers() {
        return tier.getMaxUsers();
    }
}

