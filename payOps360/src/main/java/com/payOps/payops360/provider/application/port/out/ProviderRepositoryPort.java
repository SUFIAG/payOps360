package com.payops.payops360.provider.application.port.out;

import com.payops.payops360.provider.domain.model.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * OUTPUT PORT - Provider repository
 */
public interface ProviderRepositoryPort {

    Provider save(Provider provider);

    Optional<Provider> findById(Long id);

    Optional<Provider> findByProviderId(String providerId);

    Page<Provider> findAll(Pageable pageable);

    Page<Provider> findByIsActive(boolean isActive, Pageable pageable);

    boolean existsByProviderId(String providerId);

    void delete(Provider provider);
}

