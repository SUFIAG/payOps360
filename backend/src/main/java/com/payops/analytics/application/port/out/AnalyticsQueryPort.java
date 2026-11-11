package com.payops.analytics.application.port.out;

import com.payops.analytics.domain.AnalyticsModels.Breakdown;
import com.payops.analytics.domain.AnalyticsModels.Corridor;
import com.payops.analytics.domain.AnalyticsModels.ProviderPerformance;
import com.payops.analytics.domain.AnalyticsModels.TimeBucket;
import com.payops.analytics.domain.AnalyticsModels.WindowStats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface AnalyticsQueryPort {

    WindowStats windowStats(long organizationId, Instant from, Instant to);

    List<TimeBucket> timeseries(long organizationId, Instant from, Instant to, Duration bucket);

    List<ProviderPerformance> providerPerformance(long organizationId, Instant from, Instant to);

    List<Breakdown> failureReasons(long organizationId, Instant from, Instant to);

    List<Breakdown> methodMix(long organizationId, Instant from, Instant to);

    List<Breakdown> riskDistribution(long organizationId, Instant from, Instant to);

    List<Corridor> corridors(long organizationId, Instant from, Instant to);
}
