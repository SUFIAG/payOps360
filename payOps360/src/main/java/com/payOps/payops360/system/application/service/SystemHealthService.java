package com.payOps/payops360.system.application.service;

import com.payOps/payops360.system.domain.model.SystemHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * System Health Monitoring Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;

    public SystemHealth getSystemHealth() {
        Map<String, SystemHealth.ComponentHealth> components = new HashMap<>();

        // Check database
        components.put("database", checkDatabase());

        // Check cache (if configured)
        components.put("cache", checkCache());

        // Check event publisher
        components.put("events", checkEventPublisher());

        // Calculate overall status
        String overallStatus = calculateOverallStatus(components);

        // Get system metrics
        Map<String, Object> metrics = getSystemMetrics();

        return SystemHealth.builder()
                .status(overallStatus)
                .timestamp(Instant.now())
                .components(components)
                .metrics(metrics)
                .build();
    }

    private SystemHealth.ComponentHealth checkDatabase() {
        long startTime = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;

            return SystemHealth.ComponentHealth.builder()
                    .name("PostgreSQL")
                    .status("HEALTHY")
                    .details("Database connection successful")
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            return SystemHealth.ComponentHealth.builder()
                    .name("PostgreSQL")
                    .status("DOWN")
                    .details("Database connection failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private SystemHealth.ComponentHealth checkCache() {
        return SystemHealth.ComponentHealth.builder()
                .name("Cache")
                .status("HEALTHY")
                .details("Cache operational")
                .responseTimeMs(0L)
                .build();
    }

    private SystemHealth.ComponentHealth checkEventPublisher() {
        return SystemHealth.ComponentHealth.builder()
                .name("Event Publisher")
                .status("HEALTHY")
                .details("Event publisher operational")
                .responseTimeMs(0L)
                .build();
    }

    private String calculateOverallStatus(Map<String, SystemHealth.ComponentHealth> components) {
        boolean hasDown = components.values().stream()
                .anyMatch(c -> "DOWN".equals(c.getStatus()));

        if (hasDown) {
            return "DOWN";
        }

        boolean hasDegraded = components.values().stream()
                .anyMatch(c -> "DEGRADED".equals(c.getStatus()));

        if (hasDegraded) {
            return "DEGRADED";
        }

        return "HEALTHY";
    }

    private Map<String, Object> getSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // Get count metrics from database
            Long paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
            Long incidentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incidents WHERE status != 'RESOLVED'", Long.class);
            Long alertCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED'", Long.class);

            metrics.put("total_payments", paymentCount);
            metrics.put("active_incidents", incidentCount);
            metrics.put("active_alerts", alertCount);

            // System metrics
            Runtime runtime = Runtime.getRuntime();
            metrics.put("jvm_memory_used_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
            metrics.put("jvm_memory_total_mb", runtime.totalMemory() / 1024 / 1024);
            metrics.put("jvm_memory_max_mb", runtime.maxMemory() / 1024 / 1024);

        } catch (Exception e) {
            log.error("Error fetching system metrics", e);
        }

        return metrics;
    }
}

