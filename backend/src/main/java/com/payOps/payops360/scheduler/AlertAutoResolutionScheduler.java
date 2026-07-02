package com.payops.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Alert Auto-Resolution Scheduler
 * Automatically resolves alerts that have exceeded timeout
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertAutoResolutionScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Auto-resolve stale alerts every 10 minutes
     */
    @Scheduled(fixedRate = 600000, initialDelay = 120000) // 10 minutes
    public void autoResolveStaleAlerts() {
        try {
            log.info("🔍 Checking for stale alerts to auto-resolve...");

            // Get timeout from config (default 60 minutes)
            int timeoutMinutes = 60;

            String sql = """
                    UPDATE alerts
                    SET status = 'RESOLVED',
                        resolved_at = CURRENT_TIMESTAMP,
                        resolution_notes = 'Auto-resolved: No activity for ' || ? || ' minutes'
                    WHERE status NOT IN ('RESOLVED')
                      AND triggered_at < CURRENT_TIMESTAMP - INTERVAL '1 minute' * ?
                    """;

            int resolved = jdbcTemplate.update(sql, timeoutMinutes, timeoutMinutes);

            if (resolved > 0) {
                log.info("✅ Auto-resolved {} stale alerts", resolved);
            } else {
                log.debug("No stale alerts found");
            }

        } catch (Exception e) {
            log.error("❌ Failed to auto-resolve stale alerts", e);
        }
    }

    /**
     * Cleanup old resolved alerts (keep last 90 days)
     */
    @Scheduled(cron = "0 30 1 * * *") // Daily at 01:30 AM
    public void cleanupOldResolvedAlerts() {
        try {
            log.info("🧹 Cleaning up old resolved alerts...");

            Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);

            String sql = "DELETE FROM alerts WHERE status = 'RESOLVED' AND resolved_at < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old resolved alerts", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old alerts", e);
        }
    }
}

