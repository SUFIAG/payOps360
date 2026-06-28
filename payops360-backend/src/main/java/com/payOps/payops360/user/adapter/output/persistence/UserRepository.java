package com.payops.payops360.user.adapter.output.persistence;

import com.payops.payops360.user.adapter.output.persistence.entity.UserEntity;
import com.payops.payops360.user.domain.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for User entities
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Find user by email (case-insensitive)
     */
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    /**
     * Check if email exists (case-insensitive)
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Find users by organization
     */
    List<UserEntity> findByOrganizationId(Long organizationId);

    /**
     * Find active users by organization
     */
    List<UserEntity> findByOrganizationIdAndStatus(Long organizationId, UserStatus status);

    /**
     * Count users in organization
     */
    long countByOrganizationId(Long organizationId);

    /**
     * Find users by status
     */
    List<UserEntity> findByStatus(UserStatus status);
}

