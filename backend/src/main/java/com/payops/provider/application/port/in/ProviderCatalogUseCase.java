package com.payops.provider.application.port.in;

import com.payops.provider.domain.ProviderType;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProviderCatalogUseCase {

    List<ProviderView> list(long organizationId);

    ProviderView get(long organizationId, String code);

    Optional<ProviderView> findByCode(long organizationId, String code);

    Optional<ProviderView> findById(long organizationId, long providerId);

    ProviderView register(Actor actor, ProviderDefinition definition, RequestContext context);

    ProviderView update(Actor actor, String code, ProviderDefinition definition, RequestContext context);

    ProviderView setActive(Actor actor, String code, boolean active, RequestContext context);

    record ProviderDefinition(String code, String name, ProviderType type, String region, int slaLatencyMs,
                              BigDecimal slaSuccessRate, BigDecimal costBps) {
    }
}
