package com.payOps.payops360.analytics.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Time Series Metrics Data Point (Pure Domain - No Framework Dependencies)
 */
@Getter
@Builder
public class MetricDataPoint {
    private final Instant timestamp;
    private final String metricName;
    private final double value;
    private final String dimension;
    private final String aggregationType;  // SUM, AVG, MIN, MAX, COUNT

    public boolean isAnomaly(double threshold) {
        return value > threshold;
    }
}

