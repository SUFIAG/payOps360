package com.payOps.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Provider Performance Metrics Scheduler
 * Calculates daily provider performance statistics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderPerformanceMetricsScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Calculate daily provider performance metrics
     * Runs every day at 00:30 AM
     */
    @Scheduled(cron = "0 30 0 * * *") // Daily at 00:30 AM
    public void calculateDailyProviderPerformance() {
        try {
            log.info("📊 Calculating daily provider performance metrics...");

            // Check if table exists
            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'provider_performance_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ provider_performance_metrics table does not exist yet. Skipping calculation.");
                return;
            }

            LocalDate yesterday = LocalDate.now().minusDays(1);

            String sql = """
                    INSERT INTO provider_performance_metrics 
                    (id, provider_id, metric_date, total_payments, successful_payments, failed_payments,
                     success_rate, total_amount, avg_latency_ms, p95_latency_ms, p99_latency_ms, created_at)
                    SELECT 
                        gen_random_uuid(),
                        p.id as provider_id,
                        ?::date as metric_date,
                        COUNT(pm.id) as total_payments,
                        COUNT(CASE WHEN pm.status IN ('SETTLED', 'CAPTURED') THEN 1 END) as successful_payments,
                        COUNT(CASE WHEN pm.status = 'FAILED' THEN 1 END) as failed_payments,
                        COALESCE(
                            CAST(COUNT(CASE WHEN pm.status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS NUMERIC) / 
                            NULLIF(COUNT(pm.id), 0),
                            0
                        ) as success_rate,
                        COALESCE(SUM(pm.amount), 0) as total_amount,
                        COALESCE(AVG(EXTRACT(EPOCH FROM (pm.updated_at - pm.created_at)) * 1000), 0) as avg_latency_ms,
                        COALESCE(
                            PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (pm.updated_at - pm.created_at)) * 1000),
                            0
                        ) as p95_latency_ms,
                        COALESCE(
                            PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (pm.updated_at - pm.created_at)) * 1000),
                            0
                        ) as p99_latency_ms,
                        CURRENT_TIMESTAMP
                    FROM providers p
                    LEFT JOIN payments pm ON pm.provider_id = p.id
                        AND pm.created_at >= ?::date
                        AND pm.created_at < ?::date + INTERVAL '1 day'
                    GROUP BY p.id
                    HAVING COUNT(pm.id) > 0
                    ON CONFLICT (provider_id, metric_date) DO UPDATE SET
                        total_payments = EXCLUDED.total_payments,
                        successful_payments = EXCLUDED.successful_payments,
                        failed_payments = EXCLUDED.failed_payments,
                        success_rate = EXCLUDED.success_rate,
                        total_amount = EXCLUDED.total_amount,
                        avg_latency_ms = EXCLUDED.avg_latency_ms,
                        p95_latency_ms = EXCLUDED.p95_latency_ms,
                        p99_latency_ms = EXCLUDED.p99_latency_ms
                    """;

            int rows = jdbcTemplate.update(sql, yesterday, yesterday, yesterday);
            log.info("✅ Calculated {} provider performance metrics for {}", rows, yesterday);

        } catch (Exception e) {
            log.error("❌ Failed to calculate daily provider performance", e);
        }
    }

    /**
     * Calculate weekly provider performance trends
     * Runs every Monday at 01:00 AM
     */
    @Scheduled(cron = "0 0 1 * * MON") // Every Monday at 01:00 AM
    public void calculateWeeklyProviderTrends() {
        try {
            log.info("📊 Calculating weekly provider performance trends...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'provider_performance_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ provider_performance_metrics table does not exist. Skipping calculation.");
                return;
            }

            // Log weekly trends for monitoring
            String sql = """
                    SELECT 
                        provider_id,
                        COUNT(*) as days_count,
                        AVG(success_rate) as avg_success_rate,
                        AVG(avg_latency_ms) as avg_latency,
                        SUM(total_payments) as total_payments,
                        SUM(total_amount) as total_amount
                    FROM provider_performance_metrics
                    WHERE metric_date >= CURRENT_DATE - INTERVAL '7 days'
                    GROUP BY provider_id
                    ORDER BY avg_success_rate DESC
                    """;

            jdbcTemplate.query(sql, (rs) -> {
                String providerId = rs.getString("provider_id");
                int daysCount = rs.getInt("days_count");
                double avgSuccessRate = rs.getDouble("avg_success_rate");
                double avgLatency = rs.getDouble("avg_latency");
                long totalPayments = rs.getLong("total_payments");
                double totalAmount = rs.getDouble("total_amount");

                log.info("📈 Provider: {} | Days: {} | Success Rate: {}% | Avg Latency: {}ms | Payments: {} | Amount: ${}",
                        providerId, daysCount,
                        String.format("%.2f", avgSuccessRate * 100),
                        String.format("%.2f", avgLatency),
                        totalPayments,
                        String.format("%.2f", totalAmount));
            });

        } catch (Exception e) {
            log.error("❌ Failed to calculate weekly provider trends", e);
        }
    }

    /**
     * Cleanup old provider performance metrics (keep last 365 days)
     */
    @Scheduled(cron = "0 45 2 * * *") // Daily at 02:45 AM
    public void cleanupOldProviderMetrics() {
        try {
            log.info("🧹 Cleaning up old provider performance metrics...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'provider_performance_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ provider_performance_metrics table does not exist. Skipping cleanup.");
                return;
            }

            LocalDate cutoff = LocalDate.now().minus(365, ChronoUnit.DAYS);

            String sql = "DELETE FROM provider_performance_metrics WHERE metric_date < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old provider performance metrics", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old provider metrics", e);
        }
    }
}

