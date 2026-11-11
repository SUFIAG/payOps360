package com.payops.shared.adapter.web;

import com.payops.shared.application.Actor;

import java.util.List;
import java.util.UUID;

/**
 * Security principal placed in the SecurityContext by the token filter.
 * Controllers receive it via {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(long userId, long organizationId, String email, String role,
                                List<String> permissions, UUID sessionId) {

    public Actor actor() {
        return new Actor(userId, organizationId, email, role);
    }
}
