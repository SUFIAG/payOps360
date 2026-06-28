package com.payOps.payops360.user.domain.model;

/**
 * User lifecycle status
 */
public enum UserStatus {
    PENDING_INVITATION,  // Invitation sent, not yet accepted
    ACTIVE,              // Normal operations
    INACTIVE,            // Temporarily disabled
    LOCKED,              // Locked due to failed login attempts or security reasons
    SUSPENDED,           // Suspended by admin
    DEACTIVATED          // Soft deleted
}

