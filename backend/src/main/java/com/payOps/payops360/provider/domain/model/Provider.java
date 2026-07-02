package com.payops.payops360.provider.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Provider Domain Model - Pure business entity.
 *
 * Represents a payment provider with configuration and metadata.
 * NO framework dependencies - pure domain logic.
 */
@Getter
@Builder(toBuilder = true)
public class Provider {

    // Identity
    private final Long id;
    private final String providerId;  // Business identifier (e.g., "stripe", "paypal")
    private final String providerName;
    private final ProviderType providerType;

    // Configuration
    private final String baseUrl;
    private final Integer timeoutMs;
    @Builder.Default
    private final Map<String, Object> configuration = Map.of();

    // SLA Thresholds
    private final Integer slaLatencyMs;       // P95 latency SLA
    private final Double slaSuccessRate;      // % success rate SLA
    private final Double slaAvailability;     // % availability SLA

    // Status
    private boolean isActive;
    private boolean isHealthy;

    // Metadata
    @Builder.Default
    private final Map<String, String> metadata = Map.of();

    // Timestamps
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Activate provider
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Deactivate provider
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark provider as healthy
     */
    public void markHealthy() {
        this.isHealthy = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark provider as unhealthy
     */
    public void markUnhealthy() {
        this.isHealthy = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if provider is operational (active and healthy)
     */
    public boolean isOperational() {
        return isActive && isHealthy;
    }

    /**
     * Get effective timeout (with fallback)
     */
    public int getEffectiveTimeoutMs() {
        return timeoutMs != null ? timeoutMs : 30000; // Default 30s
    }

    /**
     * Check if latency breaches SLA
     */
    public boolean breachesLatencySLA(long actualLatencyMs) {
        return slaLatencyMs != null && actualLatencyMs > slaLatencyMs;
    }

    /**
     * Check if success rate breaches SLA
     */
    public boolean breachesSuccessRateSLA(double actualSuccessRate) {
        return slaSuccessRate != null && actualSuccessRate < slaSuccessRate;
    }
}

