package com.payops.identity.application.port.in;

import com.payops.shared.application.RequestContext;

/**
 * Self-service password recovery via a single-use e-mail link.
 */
public interface PasswordRecoveryUseCase {

    /**
     * Sends a reset link when the address belongs to an active account. The
     * caller observes no difference whether or not the account exists.
     */
    void requestReset(String email, RequestContext context);

    /** Sets a new password, clears lockout and signs out every session. */
    void resetPassword(String token, String newPassword, RequestContext context);
}
