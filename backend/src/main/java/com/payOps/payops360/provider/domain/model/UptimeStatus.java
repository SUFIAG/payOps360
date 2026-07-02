package com.payops.payops360.provider.domain.model;

/**
 * Provider uptime status.
 */
public enum UptimeStatus {
    UP("Provider is operational"),
    DEGRADED("Provider experiencing issues"),
    DOWN("Provider is not responding");

    private final String description;

    UptimeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

