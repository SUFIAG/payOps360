package com.payops.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Provider Health Snapshot Scheduler
 * Periodically captures provider health metrics for historical analysis
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderHealthSnapshotScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Capture provider health snapshots every 5 minutes
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000) // 5 minutes
    public void captureProviderHealthSnapshots() {
        try {
            log.info("📊 Capturing provider health snapshots...");

            String sql = """
                    INSERT INTO provider_health_snapshots 
                    (id, provider_id, success_rate, failure_rate, average_latency_ms, 
                     p95_latency_ms, p99_latency_ms, timeout_count, error_count, 
                     total_requests, sla_compliance_rate, uptime_percentage, recorded_at)
                    SELECT 
                        gen_random_uuid(),
                        p.id,
                        COALESCE(
                            CAST(COUNT(CASE WHEN pm.status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                            NULLIF(COUNT(*), 0), 
                            0.0
                        ) as success_rate,
                        COALESCE(
                            CAST(COUNT(CASE WHEN pm.status = 'FAILED' THEN 1 END) AS FLOAT) / 
                            NULLIF(COUNT(*), 0), 
                            0.0
                        ) as failure_rate,
                        COALESCE(AVG(EXTRACT(EPOCH FROM (pm.updated_at - pm.created_at)) * 1000), 0) as avg_latency,
                        COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (pm.updated_at - pm.created_at)) * 1000), 0) as p95_latency,
                        COALESCE(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (pm.updated_at - pm.created_at)) * 1000), 0) as p99_latency,
                        COUNT(CASE WHEN pm.status = 'FAILED' AND pm.error_message LIKE '%timeout%' THEN 1 END) as timeout_count,
                        COUNT(CASE WHEN pm.status = 'FAILED' THEN 1 END) as error_count,
                        COUNT(*) as total_requests,
                        0.95 as sla_compliance, -- Placeholder
                        100.0 as uptime, -- Placeholder
                        CURRENT_TIMESTAMP
                    FROM providers p
                    LEFT JOIN payments pm ON pm.provider_id = p.id 
                        AND pm.created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                    GROUP BY p.id
                    HAVING COUNT(*) > 0
                    """;

            int rows = jdbcTemplate.update(sql);
            log.info("✅ Captured {} provider health snapshots", rows);

        } catch (Exception e) {
            log.error("❌ Failed to capture provider health snapshots", e);
        }
    }

    /**
     * Cleanup old snapshots (keep last 30 days)
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupOldSnapshots() {
        try {
            log.info("🧹 Cleaning up old provider health snapshots...");

            Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

            String sql = "DELETE FROM provider_health_snapshots WHERE recorded_at < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old snapshots", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old snapshots", e);
        }
    }
}

