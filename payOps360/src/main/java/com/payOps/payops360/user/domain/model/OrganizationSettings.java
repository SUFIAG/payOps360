package com.payOps.payops360.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Organization settings and security policies
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationSettings {
    // Security
    @Builder.Default
    private boolean force2FA = true; // Force 2FA for all fintech users
    @Builder.Default
    private int sessionTimeoutMinutes = 30;
    @Builder.Default
    private int passwordMinLength = 12; // Your requirement: 12-16 chars
    @Builder.Default
    private boolean allowPasswordReuse = false;
    @Builder.Default
    private int passwordExpiryDays = 90;

    // Access control
    @Builder.Default
    private boolean userInvitationDisabled = false;
    @Builder.Default
    private boolean requireApprovalForNewUsers = true;

    // Data retention
    @Builder.Default
    private int auditLogRetentionDays = 365;
    @Builder.Default
    private int paymentDataRetentionDays = 2555; // 7 years (compliance)

    // API & Integration
    @Builder.Default
    private boolean apiAccessEnabled = true;
    @Builder.Default
    private int apiRateLimitPerMinute = 1000;

    // Notifications
    @Builder.Default
    private boolean emailNotificationsEnabled = true;
    @Builder.Default
    private boolean slackIntegrationEnabled = false;
    private String slackWebhookUrl;
}

