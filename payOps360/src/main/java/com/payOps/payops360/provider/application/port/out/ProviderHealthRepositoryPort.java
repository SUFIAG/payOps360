package com.payops.payops360.provider.application.port.out;

import com.payops.payops360.provider.domain.model.ProviderHealth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * OUTPUT PORT - Provider health repository
 */
public interface ProviderHealthRepositoryPort {

    ProviderHealth save(ProviderHealth health);

    Optional<ProviderHealth> findLatestByProviderId(String providerId);

    List<ProviderHealth> findByProviderIdAndTimeRange(
            String providerId,
            Instant start,
            Instant end
    );

    List<ProviderHealth> findRecentSnapshots(String providerId, int limit);
}

