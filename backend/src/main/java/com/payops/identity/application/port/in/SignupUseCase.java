package com.payops.identity.application.port.in;

import com.payops.shared.application.RequestContext;

/**
 * Organisation self-service sign-up (feature-flagged) and e-mail verification.
 */
public interface SignupUseCase {

    OnboardingConfig config();

    /**
     * Creates the organisation and its first administrator (pending e-mail
     * verification). When the address is already registered nothing is
     * created; the owner is notified by e-mail instead, and the caller sees
     * the same outcome (no account enumeration).
     */
    void signup(SignupCommand command, RequestContext context);

    void verifyEmail(String token, RequestContext context);

    /** Re-sends the verification link to an unverified account; silent otherwise. */
    void resendVerification(String email, RequestContext context);

    record SignupCommand(String organizationName, String fullName, String email, String password,
                         String baseCurrency) {
    }

    record OnboardingConfig(boolean selfSignupEnabled, int passwordMinLength) {
    }
}
