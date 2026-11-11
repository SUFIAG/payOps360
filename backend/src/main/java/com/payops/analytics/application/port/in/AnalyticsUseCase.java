package com.payops.analytics.application.port.in;

import com.payops.analytics.domain.AnalyticsModels.Breakdown;
import com.payops.analytics.domain.AnalyticsModels.Corridor;
import com.payops.analytics.domain.AnalyticsModels.Overview;
import com.payops.analytics.domain.AnalyticsModels.ProviderPerformance;
import com.payops.analytics.domain.AnalyticsModels.TimeBucket;

import java.time.Duration;
import java.util.List;

public interface AnalyticsUseCase {

    /** KPIs for the window ending now, compared with the preceding window of equal length. */
    Overview overview(long organizationId, Duration window);

    List<TimeBucket> timeseries(long organizationId, Duration window, Duration bucket);

    List<ProviderPerformance> providerPerformance(long organizationId, Duration window);

    List<Breakdown> failureReasons(long organizationId, Duration window);

    List<Breakdown> methodMix(long organizationId, Duration window);

    List<Breakdown> riskDistribution(long organizationId, Duration window);

    List<Corridor> corridors(long organizationId, Duration window);
}
