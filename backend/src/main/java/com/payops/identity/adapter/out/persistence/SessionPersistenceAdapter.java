package com.payops.identity.adapter.out.persistence;

import com.payops.identity.application.port.out.SessionRepository;
import com.payops.identity.domain.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SessionPersistenceAdapter implements SessionRepository {

    private final UserSessionJpaRepository jpa;

    @Override
    public UserSession save(UserSession session) {
        UserSessionEntity entity = session.id() == null ? new UserSessionEntity() : jpa.findById(session.id()).orElseThrow();
        if (entity.getId() == null) {
            entity.setPublicId(session.publicId());
            entity.setUserId(session.userId());
            entity.setFamilyId(session.familyId());
            entity.setRefreshTokenHash(session.refreshTokenHash());
            entity.setCreatedAt(session.createdAt());
        }
        entity.setIpAddress(session.ipAddress());
        entity.setUserAgent(session.userAgent());
        entity.setLastUsedAt(session.lastUsedAt());
        entity.setExpiresAt(session.expiresAt());
        entity.setRevokedAt(session.revokedAt());
        entity.setRevokedReason(session.revokedReason());
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<UserSession> findByRefreshTokenHash(String hash) {
        return jpa.findByRefreshTokenHash(hash).map(SessionPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<UserSession> findByPublicId(UUID publicId) {
        return jpa.findByPublicId(publicId).map(SessionPersistenceAdapter::toDomain);
    }

    @Override
    public List<UserSession> findActiveByUser(long userId, Instant now) {
        return jpa.findActive(userId, now).stream().map(SessionPersistenceAdapter::toDomain).toList();
    }

    @Override
    public int revokeFamily(UUID familyId, String reason, Instant now) {
        return jpa.revokeFamily(familyId, reason, now);
    }

    @Override
    public int revokeAllForUser(long userId, String reason, Instant now) {
        return jpa.revokeAllForUser(userId, reason, now);
    }

    @Override
    public boolean isActive(UUID publicId, Instant now) {
        return jpa.isActive(publicId, now);
    }

    private static UserSession toDomain(UserSessionEntity e) {
        return new UserSession(e.getId(), e.getPublicId(), e.getUserId(), e.getFamilyId(), e.getRefreshTokenHash(),
                e.getIpAddress(), e.getUserAgent(), e.getCreatedAt(), e.getLastUsedAt(), e.getExpiresAt(),
                e.getRevokedAt(), e.getRevokedReason());
    }
}
