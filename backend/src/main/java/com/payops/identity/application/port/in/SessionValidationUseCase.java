package com.payops.identity.application.port.in;

import java.util.UUID;

/**
 * Used by the security filter to reject access tokens of revoked sessions.
 */
public interface SessionValidationUseCase {

    boolean isSessionActive(UUID sessionId);
}
