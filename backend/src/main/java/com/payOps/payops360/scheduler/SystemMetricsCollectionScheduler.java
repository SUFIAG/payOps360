package com.payops.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * System Metrics Collection Scheduler
 * Collects JVM, database, and application metrics every minute
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemMetricsCollectionScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Collect system metrics every minute
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000) // Every minute
    public void collectSystemMetrics() {
        try {
            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'system_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ system_metrics table does not exist yet. Skipping metrics collection.");
                return;
            }

            Instant now = Instant.now();

            // 1. JVM Metrics
            collectJvmMetrics(now);

            // 2. Database Connection Pool Metrics
            collectDatabaseMetrics(now);

            // 3. Application Business Metrics
            collectApplicationMetrics(now);

            log.debug("📊 System metrics collected successfully");

        } catch (Exception e) {
            log.error("❌ Failed to collect system metrics", e);
        }
    }

    private void collectJvmMetrics(Instant timestamp) {
        try {
            Runtime runtime = Runtime.getRuntime();

            // Memory metrics
            long totalMemory = runtime.totalMemory() / (1024 * 1024); // MB
            long freeMemory = runtime.freeMemory() / (1024 * 1024); // MB
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB

            saveMetric(timestamp, "JVM_MEMORY_USED_MB", usedMemory, "MB", "JVM");
            saveMetric(timestamp, "JVM_MEMORY_FREE_MB", freeMemory, "MB", "JVM");
            saveMetric(timestamp, "JVM_MEMORY_TOTAL_MB", totalMemory, "MB", "JVM");
            saveMetric(timestamp, "JVM_MEMORY_MAX_MB", maxMemory, "MB", "JVM");

            // Memory utilization percentage
            double memoryUtilization = (double) usedMemory / maxMemory * 100;
            saveMetric(timestamp, "JVM_MEMORY_UTILIZATION_PCT", memoryUtilization, "percent", "JVM");

            // Processor count
            int processors = runtime.availableProcessors();
            saveMetric(timestamp, "JVM_PROCESSORS", processors, "count", "JVM");

            // Thread count
            int threadCount = Thread.activeCount();
            saveMetric(timestamp, "JVM_THREAD_COUNT", threadCount, "count", "JVM");

        } catch (Exception e) {
            log.error("Failed to collect JVM metrics", e);
        }
    }

    private void collectDatabaseMetrics(Instant timestamp) {
        try {
            // Active database connections (if using HikariCP - check if available)
            try {
                Integer activeConnections = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()",
                        Integer.class
                );
                if (activeConnections != null) {
                    saveMetric(timestamp, "DB_ACTIVE_CONNECTIONS", activeConnections, "count", "DATABASE");
                }
            } catch (Exception e) {
                log.debug("Could not query database connections: " + e.getMessage());
            }

            // Database size
            try {
                Long dbSize = jdbcTemplate.queryForObject(
                        "SELECT pg_database_size(current_database())",
                        Long.class
                );
                if (dbSize != null) {
                    saveMetric(timestamp, "DB_SIZE_BYTES", dbSize / (1024 * 1024), "MB", "DATABASE");
                }
            } catch (Exception e) {
                log.debug("Could not query database size: " + e.getMessage());
            }

            // Table row counts (key tables)
            saveTableRowCount(timestamp, "payments");
            saveTableRowCount(timestamp, "alerts");
            saveTableRowCount(timestamp, "incidents");

        } catch (Exception e) {
            log.error("Failed to collect database metrics", e);
        }
    }

    private void saveTableRowCount(Instant timestamp, String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + tableName,
                    Long.class
            );
            if (count != null) {
                saveMetric(timestamp, "TABLE_ROW_COUNT_" + tableName.toUpperCase(), count, "rows", "DATABASE");
            }
        } catch (Exception e) {
            log.debug("Could not count rows for table " + tableName + ": " + e.getMessage());
        }
    }

    private void collectApplicationMetrics(Instant timestamp) {
        try {
            // Active alerts
            Long activeAlerts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED'",
                    Long.class
            );
            if (activeAlerts != null) {
                saveMetric(timestamp, "ACTIVE_ALERTS", activeAlerts, "count", "APPLICATION");
            }

            // Active incidents
            Long activeIncidents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incidents WHERE status != 'RESOLVED'",
                    Long.class
            );
            if (activeIncidents != null) {
                saveMetric(timestamp, "ACTIVE_INCIDENTS", activeIncidents, "count", "APPLICATION");
            }

            // Payments in last 5 minutes (throughput indicator)
            Long recentPayments = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'",
                    Long.class
            );
            if (recentPayments != null) {
                saveMetric(timestamp, "PAYMENT_THROUGHPUT_5MIN", recentPayments, "count", "APPLICATION");
            }

            // Failed payments in last hour
            Long failedPayments = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE status = 'FAILED' AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'",
                    Long.class
            );
            if (failedPayments != null) {
                saveMetric(timestamp, "FAILED_PAYMENTS_1HR", failedPayments, "count", "APPLICATION");
            }

            // Success rate (last hour)
            Double successRate = jdbcTemplate.queryForObject(
                    """
                    SELECT COALESCE(
                        CAST(COUNT(CASE WHEN status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                        NULLIF(COUNT(*), 0),
                        0
                    )
                    FROM payments 
                    WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'
                    """,
                    Double.class
            );
            if (successRate != null) {
                saveMetric(timestamp, "SUCCESS_RATE_1HR", successRate * 100, "percent", "APPLICATION");
            }

        } catch (Exception e) {
            log.error("Failed to collect application metrics", e);
        }
    }

    private void saveMetric(Instant timestamp, String name, Number value, String unit, String category) {
        try {
            String sql = """
                    INSERT INTO system_metrics 
                    (id, metric_timestamp, metric_name, metric_value, metric_unit, metric_category)
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
     * Aggregate hourly system metrics
     * Runs every hour at 5 minutes past
     */
    @Scheduled(cron = "0 5 * * * *") // Every hour at :05
    public void aggregateHourlyMetrics() {
        try {
            log.info("📊 Aggregating hourly system metrics...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'system_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ system_metrics table does not exist. Skipping aggregation.");
                return;
            }

            // Log hourly averages for key metrics
            String sql = """
                    SELECT 
                        metric_name,
                        metric_category,
                        AVG(metric_value) as avg_value,
                        MIN(metric_value) as min_value,
                        MAX(metric_value) as max_value,
                        COUNT(*) as sample_count
                    FROM system_metrics
                    WHERE metric_timestamp >= DATE_TRUNC('hour', CURRENT_TIMESTAMP - INTERVAL '1 hour')
                      AND metric_timestamp < DATE_TRUNC('hour', CURRENT_TIMESTAMP)
                      AND metric_category IN ('JVM', 'APPLICATION')
                    GROUP BY metric_name, metric_category
                    ORDER BY metric_category, metric_name
                    """;

            jdbcTemplate.query(sql, (rs) -> {
                String name = rs.getString("metric_name");
                String category = rs.getString("metric_category");
                double avg = rs.getDouble("avg_value");
                double min = rs.getDouble("min_value");
                double max = rs.getDouble("max_value");
                int count = rs.getInt("sample_count");

                log.info("📈 {} [{}] | Avg: {} | Min: {} | Max: {} | Samples: {}",
                        name, category,
                        String.format("%.2f", avg),
                        String.format("%.2f", min),
                        String.format("%.2f", max),
                        count);
            });

        } catch (Exception e) {
            log.error("❌ Failed to aggregate hourly metrics", e);
        }
    }

    /**
     * Cleanup old system metrics (keep last 7 days of raw data)
     */
    @Scheduled(cron = "0 55 2 * * *") // Daily at 02:55 AM
    public void cleanupOldMetrics() {
        try {
            log.info("🧹 Cleaning up old system metrics...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'system_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ system_metrics table does not exist. Skipping cleanup.");
                return;
            }

            Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

            String sql = "DELETE FROM system_metrics WHERE metric_timestamp < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old system metrics", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old system metrics", e);
        }
    }
}

