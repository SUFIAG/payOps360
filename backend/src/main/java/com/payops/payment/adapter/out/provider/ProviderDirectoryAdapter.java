package com.payops.payment.adapter.out.provider;

import com.payops.payment.application.port.out.ProviderDirectory;
import com.payops.provider.application.port.in.ProviderCatalogUseCase;
import com.payops.provider.application.port.in.ProviderView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bridges the payment module to the provider module through its input port.
 */
@Component
@RequiredArgsConstructor
class ProviderDirectoryAdapter implements ProviderDirectory {

    private final ProviderCatalogUseCase catalog;

    @Override
    public Optional<ProviderRef> findByCode(long organizationId, String code) {
        return catalog.findByCode(organizationId, code).map(ProviderDirectoryAdapter::toRef);
    }

    @Override
    public Optional<ProviderRef> findById(long organizationId, long id) {
        return catalog.findById(organizationId, id).map(ProviderDirectoryAdapter::toRef);
    }

    private static ProviderRef toRef(ProviderView view) {
        return new ProviderRef(view.id(), view.code(), view.name(), view.active());
    }
}
