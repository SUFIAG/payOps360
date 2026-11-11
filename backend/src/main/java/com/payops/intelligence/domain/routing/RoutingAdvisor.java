package com.payops.intelligence.domain.routing;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks providers for smart routing by a weighted blend of authorisation
 * rate, latency and cost. Providers with too little traffic are scored on
 * their SLA instead of observed data (cold start).
 */
public final class RoutingAdvisor {

    private static final double W_SUCCESS = 0.55;
    private static final double W_LATENCY = 0.25;
    private static final double W_COST = 0.20;
    private static final long MIN_SAMPLE = 20;

    public record Candidate(String code, String name, long volume, double successRate, int p95LatencyMs,
                            double slaSuccessRate, int slaLatencyMs, BigDecimal costBps, boolean healthy) {
    }

    public record Ranking(String code, String name, double score, double successRate, int p95LatencyMs,
                          BigDecimal costBps, String rationale) {
    }

    private RoutingAdvisor() {
    }

    public static List<Ranking> rank(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        double maxLatency = candidates.stream().mapToInt(c -> effectiveLatency(c)).max().orElse(1);
        double maxCost = candidates.stream().mapToDouble(c -> c.costBps().doubleValue()).max().orElse(1);
        return candidates.stream()
                .map(c -> {
                    double success = effectiveSuccess(c) / 100.0;
                    double latency = 1 - effectiveLatency(c) / Math.max(maxLatency, 1);
                    double cost = maxCost == 0 ? 1 : 1 - c.costBps().doubleValue() / maxCost;
                    double score = (W_SUCCESS * success + W_LATENCY * latency + W_COST * cost) * 100;
                    if (!c.healthy()) {
                        score *= 0.6;
                    }
                    return new Ranking(c.code(), c.name(), Math.round(score * 10) / 10.0, effectiveSuccess(c),
                            effectiveLatency(c), c.costBps(), rationale(c));
                })
                .sorted(Comparator.comparingDouble(Ranking::score).reversed())
                .toList();
    }

    private static double effectiveSuccess(Candidate c) {
        return c.volume() >= MIN_SAMPLE ? c.successRate() : c.slaSuccessRate();
    }

    private static int effectiveLatency(Candidate c) {
        return c.volume() >= MIN_SAMPLE && c.p95LatencyMs() > 0 ? c.p95LatencyMs() : c.slaLatencyMs();
    }

    private static String rationale(Candidate c) {
        if (c.volume() < MIN_SAMPLE) {
            return "Limited recent traffic — ranked on contracted SLA";
        }
        String health = c.healthy() ? "" : " Currently degraded — penalised.";
        return String.format("%.1f%% success, p95 %d ms, %s bps cost.%s", c.successRate(), c.p95LatencyMs(),
                c.costBps().stripTrailingZeros().toPlainString(), health);
    }
}
