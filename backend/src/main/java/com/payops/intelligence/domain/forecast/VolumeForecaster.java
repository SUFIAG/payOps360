package com.payops.intelligence.domain.forecast;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Short-horizon volume forecast using Holt's linear exponential smoothing
 * blended with a same-hour-of-day seasonal profile when at least two days of
 * history exist. Produces an 80% prediction band from in-sample residuals.
 */
public final class VolumeForecaster {

    private static final double ALPHA = 0.4;
    private static final double BETA = 0.1;
    private static final double SEASONAL_WEIGHT = 0.6;
    private static final double Z_80 = 1.2816;
    private static final int DAY = 24;

    public record Point(Instant at, double expected, double lower, double upper) {
    }

    private VolumeForecaster() {
    }

    /**
     * @param hourly history of hourly counts, oldest first, last element = most recent full hour
     * @param lastHourStart start instant of the last element
     */
    public static List<Point> forecast(List<Double> hourly, Instant lastHourStart, int horizonHours) {
        List<Point> points = new ArrayList<>();
        if (hourly.size() < 3) {
            return points;
        }
        double level = hourly.get(0);
        double trend = hourly.get(1) - hourly.get(0);
        double squaredError = 0;
        for (int i = 1; i < hourly.size(); i++) {
            double predicted = level + trend;
            double actual = hourly.get(i);
            squaredError += Math.pow(actual - predicted, 2);
            double previousLevel = level;
            level = ALPHA * actual + (1 - ALPHA) * (level + trend);
            trend = BETA * (level - previousLevel) + (1 - BETA) * trend;
        }
        double sigma = Math.sqrt(squaredError / (hourly.size() - 1));
        boolean seasonal = hourly.size() >= 2 * DAY;

        for (int h = 1; h <= horizonHours; h++) {
            double holt = level + h * trend;
            double expected = holt;
            if (seasonal) {
                double sameHour = seasonalAverage(hourly, h);
                expected = SEASONAL_WEIGHT * sameHour + (1 - SEASONAL_WEIGHT) * holt;
            }
            expected = Math.max(0, expected);
            double band = Z_80 * sigma * Math.sqrt(h);
            points.add(new Point(lastHourStart.plus(Duration.ofHours(h)), round(expected),
                    round(Math.max(0, expected - band)), round(expected + band)));
        }
        return points;
    }

    /** Average of the same hour-of-day on previous days, relative to the forecast step. */
    private static double seasonalAverage(List<Double> hourly, int stepAhead) {
        int n = hourly.size();
        double sum = 0;
        int count = 0;
        for (int index = n - 1 + stepAhead - DAY; index >= 0; index -= DAY) {
            if (index < n) {
                sum += hourly.get(index);
                count++;
            }
        }
        return count == 0 ? hourly.get(n - 1) : sum / count;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
