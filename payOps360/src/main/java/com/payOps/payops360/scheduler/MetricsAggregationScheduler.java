package com.payOps.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Metrics Aggregation Scheduler
 * Periodically aggregates metrics for analytics and reporting
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsAggregationScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Aggregate provider performance metrics hourly
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void aggregateProviderPerformanceHourly() {
        try {
            log.info("📊 Aggregating hourly provider performance metrics...");

            String sql = """
                    INSERT INTO provider_performance_metrics 
                    (id, provider_id, metric_timestamp, time_bucket, total_requests, 
                     successful_requests, failed_requests, success_rate, failure_rate,
                     average_latency_ms, p95_latency_ms, p99_latency_ms, timeout_count, 
                     error_count, sla_compliance_rate, uptime_percentage)
                    SELECT 
                        gen_random_uuid(),
                        phs.provider_id,
                        DATE_TRUNC('hour', phs.recorded_at) as metric_timestamp,
                        'HOURLY' as time_bucket,
                        SUM(phs.total_requests) as total_requests,
                        SUM(phs.total_requests * phs.success_rate) as successful_requests,
                        SUM(phs.total_requests * phs.failure_rate) as failed_requests,
                        AVG(phs.success_rate) as success_rate,
                        AVG(phs.failure_rate) as failure_rate,
                        AVG(phs.average_latency_ms) as average_latency_ms,
                        AVG(phs.p95_latency_ms) as p95_latency_ms,
                        AVG(phs.p99_latency_ms) as p99_latency_ms,
                        SUM(phs.timeout_count) as timeout_count,
                        SUM(phs.error_count) as error_count,
                        AVG(phs.sla_compliance_rate) as sla_compliance_rate,
                        AVG(phs.uptime_percentage) as uptime_percentage
                    FROM provider_health_snapshots phs
                    WHERE phs.recorded_at >= DATE_TRUNC('hour', CURRENT_TIMESTAMP - INTERVAL '1 hour')
                      AND phs.recorded_at < DATE_TRUNC('hour', CURRENT_TIMESTAMP)
                    GROUP BY phs.provider_id, DATE_TRUNC('hour', phs.recorded_at)
                    ON CONFLICT (provider_id, metric_timestamp, time_bucket) DO NOTHING
                    """;

            int rows = jdbcTemplate.update(sql);
            log.info("✅ Aggregated {} hourly provider metrics", rows);

        } catch (Exception e) {
            log.error("❌ Failed to aggregate hourly provider metrics", e);
        }
    }

    /**
     * Aggregate provider performance metrics daily
     */
    @Scheduled(cron = "0 5 0 * * *") // Daily at 00:05 AM
    public void aggregateProviderPerformanceDaily() {
        try {
            log.info("📊 Aggregating daily provider performance metrics...");

            String sql = """
                    INSERT INTO provider_performance_metrics 
                    (id, provider_id, metric_timestamp, time_bucket, total_requests, 
                     successful_requests, failed_requests, success_rate, failure_rate,
                     average_latency_ms, p95_latency_ms, p99_latency_ms, timeout_count, 
                     error_count, sla_compliance_rate, uptime_percentage)
                    SELECT 
                        gen_random_uuid(),
                        provider_id,
                        DATE_TRUNC('day', metric_timestamp) as metric_timestamp,
                        'DAILY' as time_bucket,
                        SUM(total_requests) as total_requests,
                        SUM(successful_requests) as successful_requests,
                        SUM(failed_requests) as failed_requests,
                        AVG(success_rate) as success_rate,
                        AVG(failure_rate) as failure_rate,
                        AVG(average_latency_ms) as average_latency_ms,
                        AVG(p95_latency_ms) as p95_latency_ms,
                        AVG(p99_latency_ms) as p99_latency_ms,
                        SUM(timeout_count) as timeout_count,
                        SUM(error_count) as error_count,
                        AVG(sla_compliance_rate) as sla_compliance_rate,
                        AVG(uptime_percentage) as uptime_percentage
                    FROM provider_performance_metrics
                    WHERE time_bucket = 'HOURLY'
                      AND metric_timestamp >= DATE_TRUNC('day', CURRENT_TIMESTAMP - INTERVAL '1 day')
                      AND metric_timestamp < DATE_TRUNC('day', CURRENT_TIMESTAMP)
                    GROUP BY provider_id, DATE_TRUNC('day', metric_timestamp)
                    ON CONFLICT (provider_id, metric_timestamp, time_bucket) DO NOTHING
                    """;

            int rows = jdbcTemplate.update(sql);
            log.info("✅ Aggregated {} daily provider metrics", rows);

        } catch (Exception e) {
            log.error("❌ Failed to aggregate daily provider metrics", e);
        }
    }

    /**
     * Capture system-wide metrics every 30 seconds
     */
    @Scheduled(fixedRate = 30000, initialDelay = 10000) // 30 seconds
    public void captureSystemMetrics() {
        try {
            Instant now = Instant.now();

            // JVM Memory metrics
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            long maxMemory = runtime.maxMemory() / 1024 / 1024;

            saveMetric(now, "jvm_memory_used_mb", usedMemory, "MB", "RESOURCE");
            saveMetric(now, "jvm_memory_total_mb", totalMemory, "MB", "RESOURCE");
            saveMetric(now, "jvm_memory_max_mb", maxMemory, "MB", "RESOURCE");

            // Business metrics
            Long activeIncidents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incidents WHERE status != 'RESOLVED'", Long.class);
            saveMetric(now, "active_incidents", activeIncidents, "count", "AVAILABILITY");

            Long activeAlerts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED'", Long.class);
            saveMetric(now, "active_alerts", activeAlerts, "count", "AVAILABILITY");

            Long paymentsLast5Min = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'",
                    Long.class);
            saveMetric(now, "payment_throughput_5min", paymentsLast5Min, "count", "THROUGHPUT");

            log.debug("📊 System metrics captured successfully");

        } catch (Exception e) {
            log.error("❌ Failed to capture system metrics", e);
        }
    }

    private void saveMetric(Instant timestamp, String name, Number value, String unit, String category) {
        try {
            String sql = """
                    INSERT INTO system_metrics (id, metric_timestamp, metric_name, metric_value, metric_unit, metric_category)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            jdbcTemplate.update(sql,
                    UUID.randomUUID(),
                    timestamp,
                    name,
                    value.doubleValue(),
                    unit,
                    category);
        } catch (Exception e) {
            log.error("Failed to save metric: " + name, e);
        }
    }

    /**
     * Cleanup old system metrics (keep last 7 days)
     */
    @Scheduled(cron = "0 10 0 * * *") // Daily at 00:10 AM
    public void cleanupOldSystemMetrics() {
        try {
            log.info("🧹 Cleaning up old system metrics...");

            Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

            String sql = "DELETE FROM system_metrics WHERE recorded_at < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old system metrics", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old system metrics", e);
        }
    }
}

