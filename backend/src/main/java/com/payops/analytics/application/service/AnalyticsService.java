package com.payops.analytics.application.service;

import com.payops.analytics.application.port.in.AnalyticsUseCase;
import com.payops.analytics.application.port.out.AnalyticsQueryPort;
import com.payops.analytics.domain.AnalyticsModels.Breakdown;
import com.payops.analytics.domain.AnalyticsModels.Corridor;
import com.payops.analytics.domain.AnalyticsModels.Kpi;
import com.payops.analytics.domain.AnalyticsModels.Overview;
import com.payops.analytics.domain.AnalyticsModels.ProviderPerformance;
import com.payops.analytics.domain.AnalyticsModels.TimeBucket;
import com.payops.analytics.domain.AnalyticsModels.WindowStats;
import com.payops.shared.domain.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService implements AnalyticsUseCase {

    private static final Duration MAX_WINDOW = Duration.ofDays(90);
    private static final int MAX_BUCKETS = 500;

    private final AnalyticsQueryPort query;
    private final Clock clock;

    @Override
    public Overview overview(long organizationId, Duration window) {
        validate(window);
        Instant to = clock.instant();
        Instant from = to.minus(window);
        WindowStats current = query.windowStats(organizationId, from, to);
        WindowStats previous = query.windowStats(organizationId, from.minus(window), from);
        List<Kpi> kpis = List.of(
                new Kpi("volume", "Processed volume", current.volumeUsd().doubleValue(), previous.volumeUsd().doubleValue(), "USD"),
                new Kpi("transactions", "Transactions", current.total(), (double) previous.total(), "count"),
                new Kpi("successRate", "Success rate", current.successRate(), previous.total() == 0 ? null : previous.successRate(), "%"),
                new Kpi("p95Latency", "P95 latency", current.p95LatencyMs(), previous.total() == 0 ? null : (double) previous.p95LatencyMs(), "ms"),
                new Kpi("revenueAtRisk", "Revenue at risk", current.failedVolumeUsd().doubleValue(), previous.failedVolumeUsd().doubleValue(), "USD"),
                new Kpi("highRisk", "High-risk payments", current.highRisk(), (double) previous.highRisk(), "count"),
                new Kpi("stuck", "Stuck payments", current.stuck(), null, "count"),
                new Kpi("customers", "Active customers", current.uniqueCustomers(), (double) previous.uniqueCustomers(), "count"));
        return new Overview(from, to, kpis, current, previous);
    }

    @Override
    public List<TimeBucket> timeseries(long organizationId, Duration window, Duration bucket) {
        validate(window);
        if (bucket.compareTo(Duration.ofMinutes(1)) < 0 || window.dividedBy(bucket) > MAX_BUCKETS) {
            throw new DomainException("INVALID_BUCKET", "Bucket size must be at least 1 minute and yield at most " + MAX_BUCKETS + " points");
        }
        Instant to = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        return query.timeseries(organizationId, to.minus(window), to, bucket);
    }

    @Override
    public List<ProviderPerformance> providerPerformance(long organizationId, Duration window) {
        validate(window);
        Instant to = clock.instant();
        return query.providerPerformance(organizationId, to.minus(window), to);
    }

    @Override
    public List<Breakdown> failureReasons(long organizationId, Duration window) {
        validate(window);
        Instant to = clock.instant();
        return query.failureReasons(organizationId, to.minus(window), to);
    }

    @Override
    public List<Breakdown> methodMix(long organizationId, Duration window) {
        validate(window);
        Instant to = clock.instant();
        return query.methodMix(organizationId, to.minus(window), to);
    }

    @Override
    public List<Breakdown> riskDistribution(long organizationId, Duration window) {
        validate(window);
        Instant to = clock.instant();
        return query.riskDistribution(organizationId, to.minus(window), to);
    }

    @Override
    public List<Corridor> corridors(long organizationId, Duration window) {
        validate(window);
        Instant to = clock.instant();
        return query.corridors(organizationId, to.minus(window), to);
    }

    private static void validate(Duration window) {
        if (window.isNegative() || window.isZero() || window.compareTo(MAX_WINDOW) > 0) {
            throw new DomainException("INVALID_WINDOW", "Window must be between 1 minute and 90 days");
        }
    }
}
