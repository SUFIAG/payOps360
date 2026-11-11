package com.payops.identity.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, Long> {

    Optional<UserSessionEntity> findByRefreshTokenHash(String hash);

    Optional<UserSessionEntity> findByPublicId(UUID publicId);

    @Query("select s from UserSessionEntity s where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now")
    List<UserSessionEntity> findActive(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("select count(s) > 0 from UserSessionEntity s where s.publicId = :id and s.revokedAt is null and s.expiresAt > :now")
    boolean isActive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update UserSessionEntity s set s.revokedAt = :now, s.revokedReason = :reason where s.familyId = :family and s.revokedAt is null")
    int revokeFamily(@Param("family") UUID family, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying
    @Query("update UserSessionEntity s set s.revokedAt = :now, s.revokedReason = :reason where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("reason") String reason, @Param("now") Instant now);
}
