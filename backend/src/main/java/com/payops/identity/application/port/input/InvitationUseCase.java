package com.payops.identity.application.port.input;

import com.payops.identity.domain.Role;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.time.Instant;
import java.util.UUID;

/**
 * E-mail invitations: administrators invite, the invitee sets their own
 * password through a single-use link.
 */
public interface InvitationUseCase {

    UserView invite(Actor actor, String email, String fullName, Role role, RequestContext context);

    /** Issues a fresh link; the previous one stops working. */
    UserView resend(Actor actor, UUID userId, RequestContext context);

    /** Withdraws a pending invitation and removes the invited account. */
    void revoke(Actor actor, UUID userId, RequestContext context);

    /** Public: minimal details shown on the acceptance page. */
    InvitationDetails describe(String token);

    /**
     * Public: activates the account with the chosen password.
     *
     * @return the account's e-mail address, so the caller can sign the user in
     */
    String accept(String token, String password, RequestContext context);

    record InvitationDetails(String organizationName, String email, String fullName, Role role, Instant expiresAt) {
    }
}
