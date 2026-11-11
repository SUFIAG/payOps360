package com.payops.identity.application.port.input;

import java.util.UUID;

/**
 * Used by the security filter to reject access tokens of revoked sessions.
 */
public interface SessionValidationUseCase {

    boolean isSessionActive(UUID sessionId);
}
