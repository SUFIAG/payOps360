package com.payops.intelligence.application.service;

import com.payops.intelligence.application.port.input.InsightsUseCase.Anomaly;
import com.payops.intelligence.application.port.output.ProviderSeriesPort.Bucket;
import com.payops.intelligence.application.port.output.ProviderSeriesPort.ProviderSeries;
import com.payops.intelligence.domain.anomaly.RobustAnomalyDetector;
import com.payops.intelligence.domain.anomaly.RobustAnomalyDetector.Direction;
import com.payops.intelligence.domain.anomaly.RobustAnomalyDetector.Finding;
import com.payops.shared.domain.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies the robust anomaly detector to a provider's metric series:
 * failure-rate spikes, latency spikes, volume drops and surges, plus an
 * absolute "provider down" guard that does not need a baseline.
 */
final class AnomalyEvaluator {

    static final String FAILURE_RATE = "FAILURE_RATE_SPIKE";
    static final String LATENCY = "LATENCY_SPIKE";
    static final String VOLUME_DROP = "VOLUME_DROP";
    static final String VOLUME_SURGE = "VOLUME_SURGE";
    static final String PROVIDER_DOWN = "PROVIDER_DOWN";

    private static final int MIN_BUCKET_VOLUME = 8;
    private static final double DOWN_FAILURE_RATE = 50;

    private AnomalyEvaluator() {
    }

    static List<Anomaly> evaluate(ProviderSeries series) {
        List<Anomaly> anomalies = new ArrayList<>();
        List<Bucket> buckets = series.buckets();
        if (buckets.size() < 2) {
            return anomalies;
        }
        Bucket latest = buckets.get(buckets.size() - 1);
        List<Bucket> baseline = buckets.subList(0, buckets.size() - 1);

        if (latest.total() >= MIN_BUCKET_VOLUME && latest.failureRate() >= DOWN_FAILURE_RATE) {
            anomalies.add(new Anomaly(PROVIDER_DOWN, series.providerCode(), series.providerName(), "failure_rate",
                    round(latest.failureRate()), round(median(baseline, Bucket::failureRate)), 0, Severity.CRITICAL,
                    String.format("%s is failing %.0f%% of payments (%d of %d in the last interval). Treat as an outage and reroute.",
                            series.providerName(), latest.failureRate(), latest.failed(), latest.total())));
            return anomalies;
        }

        if (latest.total() >= MIN_BUCKET_VOLUME) {
            List<Double> failureBaseline = baseline.stream().filter(b -> b.total() >= 3).map(Bucket::failureRate).toList();
            detect(failureBaseline, latest.failureRate(), Direction.HIGH, 5).ifPresent(f -> anomalies.add(
                    new Anomaly(FAILURE_RATE, series.providerCode(), series.providerName(), "failure_rate",
                            round(f.observed()), round(f.baselineMedian()), round(f.deviationPercent()),
                            f.observed() >= 25 ? Severity.HIGH : Severity.MEDIUM,
                            String.format("Failure rate on %s jumped to %.1f%% versus a typical %.1f%% (robust z-score %.1f).",
                                    series.providerName(), f.observed(), f.baselineMedian(), f.score()))));

            List<Double> latencyBaseline = baseline.stream().filter(b -> b.total() >= 3).map(b -> (double) b.p95LatencyMs()).toList();
            detect(latencyBaseline, latest.p95LatencyMs(), Direction.HIGH, 300).ifPresent(f -> anomalies.add(
                    new Anomaly(LATENCY, series.providerCode(), series.providerName(), "p95_latency_ms",
                            round(f.observed()), round(f.baselineMedian()), round(f.deviationPercent()),
                            f.observed() >= 2 * f.baselineMedian() ? Severity.HIGH : Severity.MEDIUM,
                            String.format("p95 latency on %s rose to %.0f ms from a typical %.0f ms.",
                                    series.providerName(), f.observed(), f.baselineMedian()))));
        }

        List<Double> volumeBaseline = baseline.stream().map(b -> (double) b.total()).toList();
        double typicalVolume = median(baseline, b -> (double) b.total());
        if (typicalVolume >= 10) {
            detect(volumeBaseline, latest.total(), Direction.LOW, Math.max(5, typicalVolume * 0.4)).ifPresent(f -> anomalies.add(
                    new Anomaly(VOLUME_DROP, series.providerCode(), series.providerName(), "volume",
                            f.observed(), round(f.baselineMedian()), round(f.deviationPercent()), Severity.HIGH,
                            String.format("Traffic to %s dropped to %.0f payments from a typical %.0f — possible upstream outage or routing change.",
                                    series.providerName(), f.observed(), f.baselineMedian()))));
            detect(volumeBaseline, latest.total(), Direction.HIGH, typicalVolume * 0.6).ifPresent(f -> anomalies.add(
                    new Anomaly(VOLUME_SURGE, series.providerCode(), series.providerName(), "volume",
                            f.observed(), round(f.baselineMedian()), round(f.deviationPercent()), Severity.MEDIUM,
                            String.format("Traffic to %s surged to %.0f payments versus a typical %.0f — check for campaigns, retries storms or abuse.",
                                    series.providerName(), f.observed(), f.baselineMedian()))));
        }
        return anomalies;
    }

    private static Optional<Finding> detect(List<Double> baseline, double observed, Direction direction, double minChange) {
        return RobustAnomalyDetector.detect(baseline, observed, direction, RobustAnomalyDetector.DEFAULT_THRESHOLD, minChange);
    }

    private static double median(List<Bucket> buckets, java.util.function.ToDoubleFunction<Bucket> metric) {
        double[] values = buckets.stream().mapToDouble(metric).sorted().toArray();
        if (values.length == 0) {
            return 0;
        }
        int n = values.length;
        return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
