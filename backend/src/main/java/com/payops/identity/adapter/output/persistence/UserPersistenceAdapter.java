package com.payops.identity.adapter.output.persistence;

import com.payops.identity.application.port.output.UserRepository;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserStatus;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmailIgnoreCase(email).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findById(long id) {
        return jpa.findById(id).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByPublicId(long organizationId, UUID publicId) {
        return jpa.findByOrganizationIdAndPublicId(organizationId, publicId).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<User> findByOrganization(long organizationId, PageQuery page) {
        Page<UserEntity> result = jpa.findByOrganizationIdOrderByCreatedAtDesc(organizationId,
                PageRequest.of(page.page(), page.size()));
        return new PageResult<>(result.map(UserPersistenceAdapter::toDomain).getContent(), page.page(), page.size(),
                result.getTotalElements());
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }

    @Override
    public User save(User user) {
        UserEntity entity = user.id() == null ? new UserEntity() : jpa.findById(user.id()).orElseThrow();
        if (entity.getId() == null) {
            entity.setPublicId(user.publicId());
            entity.setOrganizationId(user.organizationId());
            entity.setCreatedAt(user.createdAt());
        }
        entity.setEmail(user.email());
        entity.setFullName(user.fullName());
        entity.setPasswordHash(user.passwordHash());
        entity.setRole(user.role().name());
        entity.setStatus(user.status().name());
        entity.setFailedLoginAttempts(user.failedLoginAttempts());
        entity.setLockedUntil(user.lockedUntil());
        entity.setMustChangePassword(user.mustChangePassword());
        entity.setPasswordChangedAt(user.passwordChangedAt());
        entity.setMfaEnabled(user.mfaEnabled());
        entity.setMfaSecretEnc(user.mfaSecretEncrypted());
        entity.setMfaLastUsedStep(user.mfaLastUsedStep());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setLastLoginIp(user.lastLoginIp());
        entity.setUpdatedAt(Instant.now());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public void delete(User user) {
        jpa.deleteById(user.id());
        jpa.flush();
    }

    private static User toDomain(UserEntity e) {
        return new User(e.getId(), e.getPublicId(), e.getOrganizationId(), e.getEmail(), e.getFullName(),
                e.getPasswordHash(), Role.valueOf(e.getRole()), UserStatus.valueOf(e.getStatus()),
                e.getFailedLoginAttempts(), e.getLockedUntil(), e.isMustChangePassword(), e.getPasswordChangedAt(),
                e.isMfaEnabled(), e.getMfaSecretEnc(), e.getMfaLastUsedStep(), e.getLastLoginAt(), e.getLastLoginIp(),
                e.getCreatedAt(), e.getVersion());
    }
}
