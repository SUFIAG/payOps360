package com.payOps.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * System Health Check Scheduler
 * Monitors overall system health every 30 seconds
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemHealthCheckScheduler {

    private final JdbcTemplate jdbcTemplate;

    private static final double CRITICAL_MEMORY_THRESHOLD = 90.0; // 90%
    private static final double WARNING_MEMORY_THRESHOLD = 75.0; // 75%
    private static final int CRITICAL_ALERT_THRESHOLD = 100;
    private static final int WARNING_ALERT_THRESHOLD = 50;

    /**
     * Perform system health check every 30 seconds
     */
    @Scheduled(fixedRate = 30000, initialDelay = 15000) // Every 30 seconds
    public void performHealthCheck() {
        try {
            boolean isHealthy = true;
            StringBuilder healthReport = new StringBuilder("🏥 System Health Check:\n");

            // 1. Check JVM Memory
            boolean memoryHealthy = checkJvmMemory(healthReport);
            isHealthy = isHealthy && memoryHealthy;

            // 2. Check Database Connectivity
            boolean dbHealthy = checkDatabaseHealth(healthReport);
            isHealthy = isHealthy && dbHealthy;

            // 3. Check Active Alerts
            boolean alertsHealthy = checkActiveAlerts(healthReport);
            isHealthy = isHealthy && alertsHealthy;

            // 4. Check Active Incidents
            boolean incidentsHealthy = checkActiveIncidents(healthReport);
            isHealthy = isHealthy && incidentsHealthy;

            // 5. Check Payment Processing
            boolean paymentsHealthy = checkPaymentProcessing(healthReport);
            isHealthy = isHealthy && paymentsHealthy;

            if (isHealthy) {
                log.debug(healthReport.toString() + "✅ Overall Status: HEALTHY");
            } else {
                log.warn(healthReport.toString() + "⚠️ Overall Status: DEGRADED");
            }

        } catch (Exception e) {
            log.error("❌ System health check failed", e);
        }
    }

    private boolean checkJvmMemory(StringBuilder report) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            report.append(String.format("  📊 Memory: %.2f%% used", memoryUsagePercent));

            if (memoryUsagePercent >= CRITICAL_MEMORY_THRESHOLD) {
                report.append(" ❌ CRITICAL\n");
                log.error("🚨 CRITICAL: JVM memory usage at {}%", String.format("%.2f", memoryUsagePercent));
                return false;
            } else if (memoryUsagePercent >= WARNING_MEMORY_THRESHOLD) {
                report.append(" ⚠️ WARNING\n");
                log.warn("⚠️ WARNING: JVM memory usage at {}%", String.format("%.2f", memoryUsagePercent));
                return true;
            } else {
                report.append(" ✅ OK\n");
                return true;
            }

        } catch (Exception e) {
            report.append(" ❌ ERROR\n");
            log.error("Failed to check JVM memory", e);
            return false;
        }
    }

    private boolean checkDatabaseHealth(StringBuilder report) {
        try {
            // Simple database connectivity check
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // Check database connection count
            Integer activeConnections = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()",
                    Integer.class
            );

            report.append(String.format("  🗄️ Database: %d active connections ✅ OK\n", activeConnections));
            return true;

        } catch (Exception e) {
            report.append("  🗄️ Database: ❌ UNAVAILABLE\n");
            log.error("🚨 CRITICAL: Database connectivity failure", e);
            return false;
        }
    }

    private boolean checkActiveAlerts(StringBuilder report) {
        try {
            Long activeAlerts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED'",
                    Long.class
            );

            if (activeAlerts == null) {
                activeAlerts = 0L;
            }

            report.append(String.format("  🚨 Active Alerts: %d", activeAlerts));

            if (activeAlerts >= CRITICAL_ALERT_THRESHOLD) {
                report.append(" ❌ CRITICAL\n");
                log.error("🚨 CRITICAL: {} active alerts", activeAlerts);
                return false;
            } else if (activeAlerts >= WARNING_ALERT_THRESHOLD) {
                report.append(" ⚠️ WARNING\n");
                log.warn("⚠️ WARNING: {} active alerts", activeAlerts);
                return true;
            } else {
                report.append(" ✅ OK\n");
                return true;
            }

        } catch (Exception e) {
            report.append("  🚨 Active Alerts: ❌ ERROR\n");
            log.error("Failed to check active alerts", e);
            return false;
        }
    }

    private boolean checkActiveIncidents(StringBuilder report) {
        try {
            Long activeIncidents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incidents WHERE status != 'RESOLVED'",
                    Long.class
            );

            if (activeIncidents == null) {
                activeIncidents = 0L;
            }

            Long criticalIncidents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incidents WHERE status != 'RESOLVED' AND severity = 'CRITICAL'",
                    Long.class
            );

            if (criticalIncidents == null) {
                criticalIncidents = 0L;
            }

            report.append(String.format("  🔥 Active Incidents: %d (Critical: %d)", activeIncidents, criticalIncidents));

            if (criticalIncidents > 0) {
                report.append(" ⚠️ WARNING\n");
                log.warn("⚠️ WARNING: {} critical incidents active", criticalIncidents);
                return true;
            } else if (activeIncidents > 10) {
                report.append(" ⚠️ WARNING\n");
                log.warn("⚠️ WARNING: {} active incidents", activeIncidents);
                return true;
            } else {
                report.append(" ✅ OK\n");
                return true;
            }

        } catch (Exception e) {
            report.append("  🔥 Active Incidents: ❌ ERROR\n");
            log.error("Failed to check active incidents", e);
            return false;
        }
    }

    private boolean checkPaymentProcessing(StringBuilder report) {
        try {
            // Check if payments are being processed (last 5 minutes)
            Long recentPayments = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'",
                    Long.class
            );

            if (recentPayments == null) {
                recentPayments = 0L;
            }

            // Calculate recent success rate
            Double successRate = jdbcTemplate.queryForObject(
                    """
                    SELECT COALESCE(
                        CAST(COUNT(CASE WHEN status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                        NULLIF(COUNT(*), 0) * 100,
                        0
                    )
                    FROM payments 
                    WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                    """,
                    Double.class
            );

            if (successRate == null) {
                successRate = 0.0;
            }

            report.append(String.format("  💳 Payments (5min): %d | Success Rate: %.2f%%", recentPayments, successRate));

            if (recentPayments > 0 && successRate < 50.0) {
                report.append(" ⚠️ WARNING\n");
                log.warn("⚠️ WARNING: Low payment success rate: {}%", String.format("%.2f", successRate));
                return true;
            } else {
                report.append(" ✅ OK\n");
                return true;
            }

        } catch (Exception e) {
            report.append("  💳 Payments: ❌ ERROR\n");
            log.error("Failed to check payment processing", e);
            return false;
        }
    }

    /**
     * Detailed health report every 5 minutes
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000) // Every 5 minutes
    public void generateDetailedHealthReport() {
        try {
            log.info("📋 Generating detailed system health report...");

            StringBuilder report = new StringBuilder("\n");
            report.append("╔══════════════════════════════════════════════════════════╗\n");
            report.append("║          PAYOPS360 SYSTEM HEALTH REPORT                  ║\n");
            report.append("╚══════════════════════════════════════════════════════════╝\n\n");

            // JVM Stats
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;

            report.append("JVM Statistics:\n");
            report.append(String.format("  • Used Memory:  %d MB\n", usedMemory));
            report.append(String.format("  • Free Memory:  %d MB\n", freeMemory));
            report.append(String.format("  • Total Memory: %d MB\n", totalMemory));
            report.append(String.format("  • Max Memory:   %d MB\n", maxMemory));
            report.append(String.format("  • Processors:   %d\n", runtime.availableProcessors()));
            report.append(String.format("  • Threads:      %d\n\n", Thread.activeCount()));

            // Database Stats
            Long totalPayments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
            Long activeAlerts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED'", Long.class);
            Long activeIncidents = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incidents WHERE status != 'RESOLVED'", Long.class);

            report.append("Database Statistics:\n");
            report.append(String.format("  • Total Payments:   %d\n", totalPayments));
            report.append(String.format("  • Active Alerts:    %d\n", activeAlerts));
            report.append(String.format("  • Active Incidents: %d\n\n", activeIncidents));

            // Recent Activity
            Long paymentsLast5Min = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'",
                    Long.class
            );
            Long paymentsLastHour = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'",
                    Long.class
            );

            report.append("Recent Activity:\n");
            report.append(String.format("  • Payments (5min):  %d\n", paymentsLast5Min));
            report.append(String.format("  • Payments (1hr):   %d\n", paymentsLastHour));
            report.append(String.format("  • Throughput:       %.2f payments/min\n\n", paymentsLastHour / 60.0));

            report.append("╚══════════════════════════════════════════════════════════╝");

            log.info(report.toString());

        } catch (Exception e) {
            log.error("❌ Failed to generate detailed health report", e);
        }
    }
}

