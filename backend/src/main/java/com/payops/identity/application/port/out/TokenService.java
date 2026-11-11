package com.payops.identity.application.port.out;

import com.payops.identity.domain.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived signed tokens.
 */
public interface TokenService {

    IssuedToken issueAccessToken(User user, UUID sessionId);

    /** Short-lived token proving the password step succeeded; required for the MFA step. */
    IssuedToken issueMfaChallengeToken(User user);

    Optional<Long> verifyMfaChallengeToken(String token);

    /** Opaque, high-entropy refresh token. Only its hash is ever stored. */
    String newRefreshToken();

    String hashOpaqueToken(String token);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
