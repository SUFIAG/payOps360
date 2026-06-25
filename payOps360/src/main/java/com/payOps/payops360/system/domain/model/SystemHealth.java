package com.payOps/payops360.system.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * System Health Status (Pure Domain)
 */
@Getter
@Builder
public class SystemHealth {
    private final String status;  // HEALTHY, DEGRADED, DOWN
    private final Instant timestamp;
    private final Map<String, ComponentHealth> components;
    private final Map<String, Object> metrics;

    @Getter
    @Builder
    public static class ComponentHealth {
        private final String name;
        private final String status;
        private final String details;
        private final Long responseTimeMs;
    }

    public boolean isHealthy() {
        return "HEALTHY".equals(status);
    }

    public boolean isDegraded() {
        return "DEGRADED".equals(status);
    }
}

