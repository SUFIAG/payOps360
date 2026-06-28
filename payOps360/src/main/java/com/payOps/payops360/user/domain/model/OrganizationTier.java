package com.payOps.payops360.user.domain.model;

import lombok.Getter;

/**
 * Organization pricing tiers with different capabilities
 */
@Getter
public enum OrganizationTier {
    STARTER(5, 1000),           // 5 users, 1K payments/month
    PROFESSIONAL(25, 10000),     // 25 users, 10K payments/month
    ENTERPRISE(100, 100000),     // 100 users, 100K payments/month
    UNLIMITED(-1, -1);           // Unlimited

    private final int maxUsers;
    private final int maxPaymentsPerMonth;

    OrganizationTier(int maxUsers, int maxPaymentsPerMonth) {
        this.maxUsers = maxUsers;
        this.maxPaymentsPerMonth = maxPaymentsPerMonth;
    }

    public boolean hasUnlimitedUsers() {
        return maxUsers == -1;
    }

    public boolean hasUnlimitedPayments() {
        return maxPaymentsPerMonth == -1;
    }
}

