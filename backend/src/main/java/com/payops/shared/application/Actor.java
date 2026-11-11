package com.payops.shared.application;

/**
 * The authenticated principal on whose behalf a use case runs.
 * Every use case is scoped to {@link #organizationId()} (tenant isolation).
 */
public record Actor(long userId, long organizationId, String email, String role) {

    public static Actor system(long organizationId) {
        return new Actor(0L, organizationId, "system@payops360", "SYSTEM");
    }

    public boolean isSystem() {
        return userId == 0L;
    }
}
