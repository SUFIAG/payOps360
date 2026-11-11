package com.payops.identity.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    Optional<UserEntity> findByOrganizationIdAndPublicId(Long organizationId, UUID publicId);

    Page<UserEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);
}
