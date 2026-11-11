package com.payops.identity.application.port.input;

import com.payops.identity.domain.Role;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.util.UUID;

public interface UserAdministrationUseCase {

    PageResult<UserView> list(Actor actor, PageQuery page);

    /** Creates a user with a one-time temporary password that must be changed at first login. */
    CreatedUser create(Actor actor, String email, String fullName, Role role, RequestContext context);

    UserView changeRole(Actor actor, UUID userId, Role role, RequestContext context);

    UserView disable(Actor actor, UUID userId, RequestContext context);

    UserView enable(Actor actor, UUID userId, RequestContext context);

    UserView unlock(Actor actor, UUID userId, RequestContext context);

    CreatedUser resetPassword(Actor actor, UUID userId, RequestContext context);

    UserView resetMfa(Actor actor, UUID userId, RequestContext context);

    record CreatedUser(UserView user, String temporaryPassword) {
    }
}
