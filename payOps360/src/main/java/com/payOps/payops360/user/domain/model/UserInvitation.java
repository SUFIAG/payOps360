package com.payOps.payops360.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * User invitation model for invite-only registration
 * Security: Time-sensitive tokens, single-use only
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvitation {
    private Long id;
    private Long organizationId;

    // Invitation details
    private String email;
    private Set<Role> roles;

    // Security
    private String token; // UUID-based secure token
    private LocalDateTime expiresAt; // 7 days default
    private boolean used;

    // Metadata
    private String invitedBy; // User email who sent invitation
    private LocalDateTime invitedAt;
    private LocalDateTime acceptedAt;
    private String acceptedFromIp;

    // Business logic
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void markAsUsed() {
        this.used = true;
        this.acceptedAt = LocalDateTime.now();
    }

    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

