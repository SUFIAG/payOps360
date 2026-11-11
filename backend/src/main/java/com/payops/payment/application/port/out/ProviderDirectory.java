package com.payops.payment.application.port.out;

import java.util.Optional;

/**
 * The payment module's view of the provider catalogue.
 */
public interface ProviderDirectory {

    Optional<ProviderRef> findByCode(long organizationId, String code);

    Optional<ProviderRef> findById(long organizationId, long id);

    record ProviderRef(long id, String code, String name, boolean active) {
    }
}
