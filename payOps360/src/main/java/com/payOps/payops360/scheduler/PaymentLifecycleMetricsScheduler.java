package com.payOps.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Payment Lifecycle Metrics Scheduler
 * Aggregates payment state duration metrics hourly
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentLifecycleMetricsScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Aggregate payment lifecycle metrics hourly
     * Tracks how long payments stay in each state
     */
    @Scheduled(cron = "0 15 * * * *") // Every hour at 15 minutes past
    public void aggregatePaymentLifecycleMetrics() {
        try {
            log.info("📊 Aggregating payment lifecycle metrics...");

            // Check if payment_lifecycle_metrics table exists
            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_lifecycle_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ payment_lifecycle_metrics table does not exist yet. Skipping aggregation.");
                return;
            }

            // Calculate state durations from payment_events
            String sql = """
                    INSERT INTO payment_lifecycle_metrics 
                    (id, payment_id, provider_id, status, duration_seconds, entry_time, exit_time, created_at)
                    SELECT 
                        gen_random_uuid(),
                        pe1.payment_id,
                        p.provider_id,
                        pe1.from_status as status,
                        EXTRACT(EPOCH FROM (pe2.created_at - pe1.created_at)) as duration_seconds,
                        pe1.created_at as entry_time,
                        pe2.created_at as exit_time,
                        CURRENT_TIMESTAMP
                    FROM payment_events pe1
                    INNER JOIN payment_events pe2 
                        ON pe1.payment_id = pe2.payment_id 
                        AND pe2.created_at > pe1.created_at
                    INNER JOIN payments p ON pe1.payment_id = p.id
                    WHERE pe1.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
                      AND pe1.created_at < CURRENT_TIMESTAMP
                      AND NOT EXISTS (
                          SELECT 1 FROM payment_lifecycle_metrics plm
                          WHERE plm.payment_id = pe1.payment_id
                            AND plm.entry_time = pe1.created_at
                      )
                    ORDER BY pe1.payment_id, pe1.created_at
                    """;

            int rows = jdbcTemplate.update(sql);
            log.info("✅ Aggregated {} payment lifecycle metrics", rows);

        } catch (Exception e) {
            log.error("❌ Failed to aggregate payment lifecycle metrics", e);
        }
    }

    /**
     * Cleanup old lifecycle metrics (keep last 90 days)
     */
    @Scheduled(cron = "0 20 2 * * *") // Daily at 02:20 AM
    public void cleanupOldLifecycleMetrics() {
        try {
            log.info("🧹 Cleaning up old payment lifecycle metrics...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_lifecycle_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ payment_lifecycle_metrics table does not exist. Skipping cleanup.");
                return;
            }

            Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);

            String sql = "DELETE FROM payment_lifecycle_metrics WHERE created_at < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old lifecycle metrics", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old lifecycle metrics", e);
        }
    }

    /**
     * Calculate average state durations for reporting
     */
    @Scheduled(cron = "0 0 4 * * *") // Daily at 04:00 AM
    public void calculateAverageStateDurations() {
        try {
            log.info("📊 Calculating average payment state durations...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_lifecycle_metrics')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ payment_lifecycle_metrics table does not exist. Skipping calculation.");
                return;
            }

            // Log average durations per state for monitoring
            String sql = """
                    SELECT 
                        status,
                        COUNT(*) as count,
                        AVG(duration_seconds) as avg_duration,
                        MIN(duration_seconds) as min_duration,
                        MAX(duration_seconds) as max_duration
                    FROM payment_lifecycle_metrics
                    WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
                    GROUP BY status
                    ORDER BY avg_duration DESC
                    """;

            jdbcTemplate.query(sql, (rs) -> {
                String status = rs.getString("status");
                long count = rs.getLong("count");
                double avgDuration = rs.getDouble("avg_duration");
                double minDuration = rs.getDouble("min_duration");
                double maxDuration = rs.getDouble("max_duration");

                log.info("📈 State: {} | Count: {} | Avg: {}s | Min: {}s | Max: {}s",
                        status, count, String.format("%.2f", avgDuration),
                        String.format("%.2f", minDuration), String.format("%.2f", maxDuration));
            });

        } catch (Exception e) {
            log.error("❌ Failed to calculate average state durations", e);
        }
    }
}

