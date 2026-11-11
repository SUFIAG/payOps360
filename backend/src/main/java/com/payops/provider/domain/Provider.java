package com.payops.provider.domain;

import com.payops.shared.domain.DomainException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * A payment service provider (acquirer, gateway, wallet, corridor...) with
 * the service levels the operator expects from it.
 */
public class Provider {

    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,59}$");

    private final Long id;
    private final long organizationId;
    private final String code;
    private String name;
    private ProviderType type;
    private String region;
    private int slaLatencyMs;
    private BigDecimal slaSuccessRate;
    private BigDecimal costBps;
    private boolean active;

    @SuppressWarnings("java:S107")
    public Provider(Long id, long organizationId, String code, String name, ProviderType type, String region,
                    int slaLatencyMs, BigDecimal slaSuccessRate, BigDecimal costBps, boolean active) {
        if (!CODE.matcher(code).matches()) {
            throw new DomainException("INVALID_PROVIDER_CODE",
                    "Provider code must be 2-60 lowercase letters, digits, '-' or '_'");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.code = code;
        update(name, type, region, slaLatencyMs, slaSuccessRate, costBps);
        this.active = active;
    }

    public final void update(String name, ProviderType type, String region, int slaLatencyMs,
                             BigDecimal slaSuccessRate, BigDecimal costBps) {
        if (slaLatencyMs < 50 || slaLatencyMs > 120_000) {
            throw new DomainException("INVALID_SLA", "Latency SLA must be between 50 ms and 120 s");
        }
        if (slaSuccessRate.compareTo(BigDecimal.valueOf(50)) < 0 || slaSuccessRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException("INVALID_SLA", "Success-rate SLA must be between 50% and 100%");
        }
        if (costBps.signum() < 0) {
            throw new DomainException("INVALID_COST", "Cost cannot be negative");
        }
        this.name = name.trim();
        this.type = type;
        this.region = region;
        this.slaLatencyMs = slaLatencyMs;
        this.slaSuccessRate = slaSuccessRate;
        this.costBps = costBps;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public String code() { return code; }
    public String name() { return name; }
    public ProviderType type() { return type; }
    public String region() { return region; }
    public int slaLatencyMs() { return slaLatencyMs; }
    public BigDecimal slaSuccessRate() { return slaSuccessRate; }
    public BigDecimal costBps() { return costBps; }
    public boolean active() { return active; }
}
