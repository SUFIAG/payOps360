package com.payops.payops360.provider.application.port.in;

import com.payops.payops360.provider.domain.model.Provider;
import com.payops.payops360.provider.domain.model.ProviderHealth;

/**
 * INPUT PORT - Get provider information
 */
public interface GetProviderUseCase {

    Provider getById(Long id);

    Provider getByProviderId(String providerId);

    ProviderHealth getHealth(String providerId);
}

