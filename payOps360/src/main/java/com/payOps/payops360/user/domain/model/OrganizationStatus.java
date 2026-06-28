package com.payOps.payops360.user.domain.model;

/**
 * Organization lifecycle status
 */
public enum OrganizationStatus {
    ACTIVE,          // Normal operations
    SUSPENDED,       // Temporarily disabled (payment issues, compliance)
    DEACTIVATED,     // Soft deleted
    PENDING_SETUP    // Initial registration, not yet activated
}

