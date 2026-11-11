package com.payops.intelligence.application.service;

import com.payops.analytics.domain.AnalyticsModels;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.intelligence.application.port.in.AnomalyScanUseCase;
import com.payops.intelligence.application.port.in.InsightsUseCase;
import com.payops.intelligence.application.port.out.AlertingPort;
import com.payops.intelligence.application.port.out.OperationsDataPort;
import com.payops.intelligence.application.port.out.ProviderSeriesPort;
import com.payops.intelligence.domain.failure.FailureCategory;
import com.payops.intelligence.domain.failure.FailureKnowledgeBase;
import com.payops.intelligence.domain.forecast.VolumeForecaster;
import com.payops.intelligence.domain.routing.RoutingAdvisor;
import com.payops.provider.application.port.in.ProviderView;
import com.payops.provider.domain.HealthStatus;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightsService implements InsightsUseCase, AnomalyScanUseCase {

    private static final Duration SCAN_WINDOW = Duration.ofHours(3);
    private static final Duration SCAN_BUCKET = Duration.ofMinutes(5);
    private static final Duration CACHE_TTL = Duration.ofSeconds(20);
    private static final int FORECAST_HOURS = 12;

    private final OperationsDataPort data;
    private final ProviderSeriesPort series;
    private final AlertingPort alerting;
    private final AiInvoker ai;
    private final Clock clock;
    private final Map<Long, OperationalInsights> cache = new ConcurrentHashMap<>();

    // --------------------------------------------------------------- insights

    @Override
    public OperationalInsights insights(long organizationId) {
        OperationalInsights cached = cache.get(organizationId);
        if (cached != null && cached.generatedAt().plus(CACHE_TTL).isAfter(clock.instant())) {
            return cached;
        }
        OperationalInsights fresh = compute(organizationId);
        cache.put(organizationId, fresh);
        return fresh;
    }

    private OperationalInsights compute(long organizationId) {
        AnalyticsModels.Overview lastHour = data.overview(organizationId, Duration.ofHours(1));
        List<Anomaly> anomalies = anomaliesFor(organizationId);
        List<IncidentView> incidents = data.activeIncidents(organizationId);
        List<AnalyticsModels.ProviderPerformance> performance = data.providerPerformance(organizationId, Duration.ofHours(1));
        List<ProviderView> providers = data.providers(organizationId);
        List<RoutingAdvisor.Ranking> routing = routing(performance, providers);
        List<VolumeForecaster.Point> forecast = forecast(organizationId);
        List<AnalyticsModels.Breakdown> failures = data.failureReasons(organizationId, Duration.ofHours(1));

        int score = healthScore(lastHour.current(), anomalies, incidents);
        String status = score >= 85 ? "OPTIMAL" : score >= 70 ? "WATCH" : score >= 50 ? "DEGRADED" : "CRITICAL";
        return new OperationalInsights(score, status, headline(lastHour.current(), anomalies, incidents),
                anomalies,
                recommendations(lastHour.current(), anomalies, routing, failures, forecast, performance),
                forecast, routing, ai.deterministicEngine(), clock.instant());
    }

    // -------------------------------------------------------------- scanning

    @Override
    public int scan() {
        Instant to = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        to = to.minusSeconds(to.getEpochSecond() % SCAN_BUCKET.toSeconds());
        int found = 0;
        for (ProviderSeriesPort.ProviderSeries s : series.series(to.minus(SCAN_WINDOW), to, SCAN_BUCKET)) {
            for (Anomaly anomaly : AnomalyEvaluator.evaluate(s)) {
                alerting.raiseAnomaly(s.organizationId(), s.providerId(), anomaly);
                found++;
            }
        }
        return found;
    }

    private List<Anomaly> anomaliesFor(long organizationId) {
        Instant to = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        to = to.minusSeconds(to.getEpochSecond() % SCAN_BUCKET.toSeconds());
        return series.series(to.minus(SCAN_WINDOW), to, SCAN_BUCKET).stream()
                .filter(s -> s.organizationId() == organizationId)
                .flatMap(s -> AnomalyEvaluator.evaluate(s).stream())
                .sorted(Comparator.comparing(Anomaly::severity).reversed())
                .toList();
    }

    // ------------------------------------------------------------ components

    private List<VolumeForecaster.Point> forecast(long organizationId) {
        List<AnalyticsModels.TimeBucket> hourly = data.hourlySeries(organizationId, Duration.ofHours(72));
        if (hourly.isEmpty()) {
            return List.of();
        }
        List<Double> counts = hourly.stream().map(b -> (double) b.total()).toList();
        return VolumeForecaster.forecast(counts, hourly.get(hourly.size() - 1).bucket(), FORECAST_HOURS);
    }

    static List<RoutingAdvisor.Ranking> routing(List<AnalyticsModels.ProviderPerformance> performance,
                                                List<ProviderView> providers) {
        Map<String, ProviderView> byCode = providers.stream()
                .collect(Collectors.toMap(ProviderView::code, Function.identity()));
        List<RoutingAdvisor.Candidate> candidates = performance.stream()
                .filter(p -> byCode.containsKey(p.code()) && byCode.get(p.code()).active())
                .map(p -> {
                    HealthStatus health = byCode.get(p.code()).health().status();
                    return new RoutingAdvisor.Candidate(p.code(), p.name(), p.succeeded() + p.failed(), p.successRate(),
                            p.p95LatencyMs(), p.slaSuccessRate().doubleValue(), p.slaLatencyMs(), p.costBps(),
                            health != HealthStatus.CRITICAL && health != HealthStatus.DEGRADED);
                })
                .toList();
        return RoutingAdvisor.rank(candidates);
    }

    static int healthScore(AnalyticsModels.WindowStats stats, List<Anomaly> anomalies, List<IncidentView> incidents) {
        double score = 100;
        if (stats.succeeded() + stats.failed() > 0) {
            score -= Math.min(40, Math.max(0, 98 - stats.successRate()) * 3);
        }
        if (stats.p95LatencyMs() > 2000) {
            score -= 10;
        } else if (stats.p95LatencyMs() > 1200) {
            score -= 5;
        }
        for (Anomaly a : anomalies) {
            score -= switch (a.severity()) {
                case CRITICAL -> 25;
                case HIGH -> 12;
                case MEDIUM -> 5;
                case LOW -> 2;
            };
        }
        for (IncidentView i : incidents) {
            score -= i.severity() == Severity.CRITICAL ? 15 : i.severity() == Severity.HIGH ? 8 : 3;
        }
        score -= Math.min(10, stats.stuck());
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private static String headline(AnalyticsModels.WindowStats stats, List<Anomaly> anomalies, List<IncidentView> incidents) {
        if (stats.total() == 0) {
            return "No payment traffic in the last hour. Monitoring is active and will flag anomalies as soon as traffic resumes.";
        }
        if (!anomalies.isEmpty()) {
            Anomaly top = anomalies.get(0);
            String incidentNote = incidents.isEmpty() ? "" : " " + incidents.size() + " incident(s) open.";
            return "Attention: " + top.explanation() + incidentNote;
        }
        if (!incidents.isEmpty()) {
            return String.format("%d active incident(s) under management. Overall success rate %.1f%% across %,d payments in the last hour.",
                    incidents.size(), stats.successRate(), stats.total());
        }
        return String.format("All systems nominal — %.1f%% success across %,d payments in the last hour, p95 latency %d ms.",
                stats.successRate(), stats.total(), stats.p95LatencyMs());
    }

    private static List<Recommendation> recommendations(AnalyticsModels.WindowStats stats, List<Anomaly> anomalies,
                                                        List<RoutingAdvisor.Ranking> routing,
                                                        List<AnalyticsModels.Breakdown> failures,
                                                        List<VolumeForecaster.Point> forecast,
                                                        List<AnalyticsModels.ProviderPerformance> performance) {
        List<Recommendation> recs = new ArrayList<>();
        for (Anomaly anomaly : anomalies) {
            if (anomaly.type().equals(AnomalyEvaluator.PROVIDER_DOWN) || anomaly.type().equals(AnomalyEvaluator.FAILURE_RATE)) {
                routing.stream().filter(r -> !r.code().equals(anomaly.providerCode())).findFirst().ifPresent(best ->
                        recs.add(new Recommendation(anomaly.severity() == Severity.CRITICAL ? 1 : 2, "ROUTING",
                                "Reroute traffic away from " + anomaly.providerName(),
                                String.format("%s is the best alternative right now (score %.0f, %.1f%% success).",
                                        best.name(), best.score(), best.successRate()),
                                "Shift new " + anomaly.providerCode() + " traffic to " + best.code())));
            }
        }
        long completed = stats.succeeded() + stats.failed();
        if (completed > 0 && !failures.isEmpty()) {
            AnalyticsModels.Breakdown top = failures.get(0);
            FailureKnowledgeBase.FailureInsight insight = FailureKnowledgeBase.lookup(top.label());
            double share = top.count() * 100.0 / Math.max(1, stats.failed());
            if (share >= 30 && top.count() >= 5) {
                String category = insight.category() == FailureCategory.TECHNICAL ? "RELIABILITY" : "CONVERSION";
                recs.add(new Recommendation(insight.category() == FailureCategory.TECHNICAL ? 2 : 3, category,
                        String.format("%s drives %.0f%% of failures", top.label(), share),
                        insight.meaning() + (insight.retryable() ? " Retryable." : " Not retryable."),
                        insight.operatorAction()));
            }
        }
        if (stats.stuck() > 0) {
            recs.add(new Recommendation(2, "OPERATIONS", stats.stuck() + " payment(s) stuck in processing",
                    "These exceeded their state's time budget and may need reconciliation with the provider.",
                    "Open Payments → filter 'Stuck' and reconcile"));
        }
        if (stats.highRisk() > 0) {
            recs.add(new Recommendation(3, "RISK", stats.highRisk() + " high-risk payment(s) in the last hour",
                    "Scored HIGH or CRITICAL by the real-time risk engine.",
                    "Review in Payments → Risk ≥ HIGH"));
        }
        costOpportunity(performance).ifPresent(recs::add);
        if (!forecast.isEmpty() && stats.total() > 0) {
            double nextHour = forecast.get(0).expected();
            if (nextHour > stats.total() * 1.5 && nextHour - stats.total() >= 20) {
                recs.add(new Recommendation(3, "CAPACITY", "Traffic surge expected next hour",
                        String.format("Forecast %.0f payments vs %d in the last hour.", nextHour, stats.total()),
                        "Confirm provider rate limits and on-call coverage"));
            }
        }
        return recs.stream().sorted(Comparator.comparingInt(Recommendation::priority)).limit(6).toList();
    }

    private static java.util.Optional<Recommendation> costOpportunity(List<AnalyticsModels.ProviderPerformance> performance) {
        List<AnalyticsModels.ProviderPerformance> busy = performance.stream()
                .filter(p -> p.succeeded() + p.failed() >= 30).toList();
        for (AnalyticsModels.ProviderPerformance expensive : busy) {
            for (AnalyticsModels.ProviderPerformance cheaper : busy) {
                if (Objects.equals(expensive.code(), cheaper.code())) {
                    continue;
                }
                double bpsSaved = expensive.costBps().doubleValue() - cheaper.costBps().doubleValue();
                if (bpsSaved >= 10 && cheaper.successRate() >= expensive.successRate() - 0.5) {
                    return java.util.Optional.of(new Recommendation(4, "COST",
                            "Save ~" + Math.round(bpsSaved) + " bps by shifting volume to " + cheaper.name(),
                            String.format("%s matches %s on success rate (%.1f%% vs %.1f%%) at lower cost.",
                                    cheaper.name(), expensive.name(), cheaper.successRate(), expensive.successRate()),
                            "Increase routing weight for " + cheaper.code()));
                }
            }
        }
        return java.util.Optional.empty();
    }
}
