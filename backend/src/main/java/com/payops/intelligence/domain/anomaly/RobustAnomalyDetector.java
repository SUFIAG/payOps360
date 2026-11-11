package com.payops.intelligence.domain.anomaly;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Detects outliers with the modified z-score (Iglewicz &amp; Hoaglin), which
 * uses the median and the median absolute deviation instead of mean and
 * standard deviation. It stays reliable when the baseline itself contains
 * spikes, which is typical of payment traffic.
 */
public final class RobustAnomalyDetector {

    /** Conventional cut-off for the modified z-score. */
    public static final double DEFAULT_THRESHOLD = 3.5;
    private static final double CONSISTENCY = 0.6745;
    private static final int MIN_BASELINE = 6;

    public enum Direction { HIGH, LOW }

    public record Finding(double observed, double baselineMedian, double score, Direction direction) {

        public double deviationPercent() {
            return baselineMedian == 0 ? 100 : (observed - baselineMedian) * 100 / Math.abs(baselineMedian);
        }
    }

    private RobustAnomalyDetector() {
    }

    /**
     * @param baseline historical observations (excluding {@code observed})
     * @param minimumAbsoluteChange ignore statistically significant but operationally tiny moves
     */
    public static Optional<Finding> detect(List<Double> baseline, double observed, Direction direction,
                                           double threshold, double minimumAbsoluteChange) {
        if (baseline.size() < MIN_BASELINE) {
            return Optional.empty();
        }
        double[] values = baseline.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double median = median(values);
        double[] deviations = Arrays.stream(values).map(v -> Math.abs(v - median)).sorted().toArray();
        double mad = median(deviations);
        // Floor MAD so a perfectly flat baseline does not make every change "infinite".
        double scale = Math.max(mad, Math.max(Math.abs(median) * 0.05, 1e-6));
        double score = CONSISTENCY * (observed - median) / scale;
        boolean anomalous = direction == Direction.HIGH
                ? score >= threshold && observed - median >= minimumAbsoluteChange
                : score <= -threshold && median - observed >= minimumAbsoluteChange;
        return anomalous ? Optional.of(new Finding(observed, median, score, direction)) : Optional.empty();
    }

    static double median(double[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
