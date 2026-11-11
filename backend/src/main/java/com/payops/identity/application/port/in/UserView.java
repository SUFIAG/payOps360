package com.payops.identity.application.port.in;

import com.payops.identity.domain.Permission;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserView(
        UUID id,
        String email,
        String fullName,
        Role role,
        Set<Permission> permissions,
        UserStatus status,
        boolean mfaEnabled,
        boolean mustChangePassword,
        boolean locked,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserView of(User user, Instant now) {
        return new UserView(user.publicId(), user.email(), user.fullName(), user.role(), user.role().permissions(),
                user.status(), user.mfaEnabled(), user.mustChangePassword(), user.isLocked(now),
                user.lastLoginAt(), user.createdAt());
    }
}
