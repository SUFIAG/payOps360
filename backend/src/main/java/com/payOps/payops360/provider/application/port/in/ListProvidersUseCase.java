package com.payops.payops360.provider.application.port.in;

import com.payops.payops360.provider.domain.model.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * INPUT PORT - List providers
 */
public interface ListProvidersUseCase {

    Page<Provider> listAll(Pageable pageable);

    Page<Provider> listActive(Pageable pageable);
}

