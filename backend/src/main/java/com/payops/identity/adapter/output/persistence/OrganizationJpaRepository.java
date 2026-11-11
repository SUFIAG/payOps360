package com.payops.identity.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, Long> {

    List<OrganizationEntity> findByStatus(String status);

    boolean existsBySlug(String slug);
}
