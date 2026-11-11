package com.payops.provider.application.port.output;

import com.payops.provider.domain.Provider;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository {

    List<Provider> findByOrganization(long organizationId);

    List<Provider> findAllActive();

    Optional<Provider> findByCode(long organizationId, String code);

    Optional<Provider> findById(long organizationId, long id);

    boolean existsByCode(long organizationId, String code);

    Provider save(Provider provider);
}
