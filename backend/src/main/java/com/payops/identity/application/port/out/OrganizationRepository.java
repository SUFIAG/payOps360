package com.payops.identity.application.port.out;

import com.payops.identity.domain.Organization;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(long id);

    List<Organization> findAllActive();

    Organization save(Organization organization);

    long count();

    boolean existsBySlug(String slug);
}
