package com.payops.identity.adapter.config;

import com.payops.identity.application.service.IdentitySettings;
import com.payops.identity.application.service.OnboardingSettings;
import com.payops.identity.domain.LockoutPolicy;
import com.payops.identity.domain.PasswordPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class IdentityConfig {

    @Bean
    IdentitySettings identitySettings(SecurityProperties properties) {
        return new IdentitySettings(
                new LockoutPolicy(properties.lockout().maxFailedAttempts(), properties.lockout().lockDuration()),
                new PasswordPolicy(properties.password().minLength(), properties.password().historyDepth()),
                Duration.ofDays(properties.password().maxAgeDays()),
                properties.refreshTokenTtl(),
                properties.lockout().maxIpFailuresPer15Minutes(),
                "PayOps360");
    }

    @Bean
    OnboardingSettings onboardingSettings(OnboardingProperties onboarding, AppProperties app) {
        if (app.baseUrl() == null || app.baseUrl().isBlank()) {
            throw new IllegalStateException("payops.app.base-url (APP_BASE_URL) must be set; e-mail links point to it");
        }
        return new OnboardingSettings(app.baseUrl(), onboarding.selfSignupEnabled(),
                onboarding.newOrganizationMfaRequired());
    }
}
