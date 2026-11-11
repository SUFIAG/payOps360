package com.payops.provider.application.port.input;

import com.payops.provider.domain.HealthSnapshot;
import com.payops.provider.domain.HealthStatus;
import com.payops.provider.domain.Provider;
import com.payops.provider.domain.ProviderType;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderView(long id, String code, String name, ProviderType type, String region, int slaLatencyMs,
                           BigDecimal slaSuccessRate, BigDecimal costBps, boolean active, Health health) {

    public static ProviderView of(Provider provider, HealthSnapshot snapshot) {
        Health health = snapshot == null
                ? new Health(HealthStatus.UNKNOWN, null, null, null, 0, null)
                : new Health(snapshot.status(), snapshot.successRate(), snapshot.avgLatencyMs(),
                snapshot.p95LatencyMs(), snapshot.total(), snapshot.windowEnd());
        return new ProviderView(provider.id(), provider.code(), provider.name(), provider.type(), provider.region(),
                provider.slaLatencyMs(), provider.slaSuccessRate(), provider.costBps(), provider.active(), health);
    }

    public record Health(HealthStatus status, BigDecimal successRate, Integer avgLatencyMs, Integer p95LatencyMs,
                         int volume, Instant observedAt) {
    }
}
