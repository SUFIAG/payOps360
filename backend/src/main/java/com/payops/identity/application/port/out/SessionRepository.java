package com.payops.identity.application.port.out;

import com.payops.identity.domain.UserSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    UserSession save(UserSession session);

    Optional<UserSession> findByRefreshTokenHash(String hash);

    Optional<UserSession> findByPublicId(UUID publicId);

    List<UserSession> findActiveByUser(long userId, Instant now);

    int revokeFamily(UUID familyId, String reason, Instant now);

    int revokeAllForUser(long userId, String reason, Instant now);

    boolean isActive(UUID publicId, Instant now);
}
