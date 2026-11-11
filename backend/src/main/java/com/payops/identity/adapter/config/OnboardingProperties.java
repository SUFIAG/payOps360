package com.payops.identity.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param selfSignupEnabled          allow anyone to create an organisation (off by default)
 * @param newOrganizationMfaRequired MFA policy for self-registered organisations
 * @param recoveryPerIpPerMinute     per-IP budget for recovery, invitation and sign-up endpoints
 */
@ConfigurationProperties(prefix = "payops.onboarding")
public record OnboardingProperties(boolean selfSignupEnabled, boolean newOrganizationMfaRequired,
                                   int recoveryPerIpPerMinute) {
}
