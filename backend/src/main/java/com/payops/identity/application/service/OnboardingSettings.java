package com.payops.identity.application.service;

/**
 * Plain settings object for onboarding flows.
 *
 * @param appBaseUrl                 public URL of the web console, used to build e-mail links
 * @param selfSignupEnabled          whether anyone may create a new organisation
 * @param newOrganizationMfaRequired MFA policy applied to self-registered organisations
 */
public record OnboardingSettings(String appBaseUrl, boolean selfSignupEnabled, boolean newOrganizationMfaRequired) {

    public OnboardingSettings {
        appBaseUrl = appBaseUrl == null ? "" : appBaseUrl.replaceAll("/+$", "");
    }
}
