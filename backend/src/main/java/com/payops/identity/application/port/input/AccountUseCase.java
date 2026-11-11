package com.payops.identity.application.port.input;

import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.util.List;
import java.util.UUID;

/**
 * Self-service operations for the signed-in user.
 */
public interface AccountUseCase {

    UserView me(Actor actor);

    String organizationName(Actor actor);

    void changePassword(Actor actor, String currentPassword, String newPassword, UUID currentSessionId,
                        RequestContext context);

    MfaEnrollment beginMfaEnrollment(Actor actor);

    List<String> confirmMfaEnrollment(Actor actor, String code, RequestContext context);

    void disableMfa(Actor actor, String password, String code, RequestContext context);

    List<String> regenerateRecoveryCodes(Actor actor, String code, RequestContext context);

    List<SessionView> sessions(Actor actor, UUID currentSessionId);

    void revokeSession(Actor actor, UUID sessionId, RequestContext context);
}
