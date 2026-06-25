package com.payOps.payops360.analytics.adapter.output.persistence;

import com.payOps.payops360.analytics.application.port.output.AnalyticsDataRepository;
import com.payOps.payops360.analytics.domain.model.MetricDataPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Analytics Data Repository Implementation (using JdbcTemplate for performance)
 * Queries existing tables (payments, providers) to generate metrics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsDataRepositoryAdapter implements AnalyticsDataRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<MetricDataPoint> getPaymentMetrics(Instant startTime, Instant endTime, String dimension) {
        log.info("Fetching payment metrics from {} to {} with dimension {}", startTime, endTime, dimension);

        String sql = """
                SELECT 
                    DATE_TRUNC('hour', created_at) as time_bucket,
                    COUNT(*) as payment_count,
                    SUM(CASE WHEN status IN ('SETTLED', 'CAPTURED') THEN 1 ELSE 0 END)::FLOAT / COUNT(*) as success_rate
                FROM payments
                WHERE created_at BETWEEN ? AND ?
                GROUP BY time_bucket
                ORDER BY time_bucket
                """;

        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> MetricDataPoint.builder()
                            .timestamp(rs.getTimestamp("time_bucket").toInstant())
                            .metricName("payment_success_rate")
                            .value(rs.getDouble("success_rate"))
                            .dimension("HOURLY")
                            .aggregationType("AVG")
                            .build(),
                    java.sql.Timestamp.from(startTime),
                    java.sql.Timestamp.from(endTime)
            );
        } catch (Exception e) {
            log.error("Error fetching payment metrics", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<MetricDataPoint> getProviderMetrics(String providerId, Instant startTime, Instant endTime) {
        log.info("Fetching provider metrics for {} from {} to {}", providerId, startTime, endTime);

        String sql = """
                SELECT 
                    recorded_at as time_bucket,
                    success_rate,
                    average_latency_ms
                FROM provider_health_snapshots
                WHERE provider_id = ? AND recorded_at BETWEEN ? AND ?
                ORDER BY recorded_at
                """;

        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> MetricDataPoint.builder()
                            .timestamp(rs.getTimestamp("time_bucket").toInstant())
                            .metricName("provider_success_rate")
                            .value(rs.getDouble("success_rate"))
                            .dimension(providerId)
                            .aggregationType("AVG")
                            .build(),
                    providerId,
                    java.sql.Timestamp.from(startTime),
                    java.sql.Timestamp.from(endTime)
            );
        } catch (Exception e) {
            log.error("Error fetching provider metrics", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveMetricDataPoint(MetricDataPoint dataPoint) {
        // For Phase 4, we don't persist analytics (we query on-demand)
        // In Phase 6, this would write to a time-series DB or cache
        log.debug("Metric data point recorded: {} = {} at {}",
                dataPoint.getMetricName(), dataPoint.getValue(), dataPoint.getTimestamp());
    }
}

