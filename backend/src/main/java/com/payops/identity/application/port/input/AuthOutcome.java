package com.payops.identity.application.port.input;

import java.time.Instant;
import java.util.List;

/**
 * Result of an authentication step: either a full session or a request to
 * complete (or enrol in) multi-factor authentication.
 */
public sealed interface AuthOutcome {

    record Authenticated(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                         Instant refreshTokenExpiresAt, UserView user, String organizationName,
                         List<String> recoveryCodes) implements AuthOutcome {
    }

    record MfaRequired(String challengeToken, Instant expiresAt, boolean enrollmentRequired) implements AuthOutcome {
    }
}
