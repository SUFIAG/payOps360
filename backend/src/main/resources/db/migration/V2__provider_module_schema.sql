-- PayOps360 Database Schema - Version 2
-- Provider Module

-- ============================================
-- PROVIDERS TABLE
-- ============================================
CREATE TABLE providers (
    id BIGSERIAL PRIMARY KEY,
    provider_id VARCHAR(100) UNIQUE NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    provider_type VARCHAR(50) NOT NULL,

    -- Configuration
    base_url VARCHAR(500),
    timeout_ms INT,
    configuration JSONB,

    -- SLA Thresholds
    sla_latency_ms INT,
    sla_success_rate DECIMAL(5,2),
    sla_availability DECIMAL(5,2),

    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_healthy BOOLEAN DEFAULT TRUE,

    -- Metadata
    metadata JSONB,

    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for providers
CREATE INDEX idx_provider_provider_id ON providers(provider_id);
CREATE INDEX idx_provider_active ON providers(is_active);
CREATE INDEX idx_provider_type ON providers(provider_type);

-- ============================================
-- PROVIDER HEALTH SNAPSHOTS TABLE
-- ============================================
CREATE TABLE provider_health_snapshots (
    id BIGSERIAL PRIMARY KEY,
    provider_id VARCHAR(100) NOT NULL REFERENCES providers(provider_id),

    -- Time Window
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL,

    -- Volume Metrics
    success_count BIGINT DEFAULT 0,
    failure_count BIGINT DEFAULT 0,
    timeout_count BIGINT DEFAULT 0,
    total_count BIGINT DEFAULT 0,

    -- Calculated Rates
    success_rate DECIMAL(5,2),
    failure_rate DECIMAL(5,2),
    timeout_rate DECIMAL(5,2),

    -- Latency Metrics (milliseconds)
    avg_latency_ms BIGINT,
    p50_latency_ms BIGINT,
    p95_latency_ms BIGINT,
    p99_latency_ms BIGINT,

    -- Error Distribution
    error_distribution JSONB,

    -- Health Assessment
    health_status VARCHAR(20),
    uptime_status VARCHAR(20),
    sla_compliance DECIMAL(5,2)
);

-- Indexes for provider health
CREATE INDEX idx_health_provider ON provider_health_snapshots(provider_id);
CREATE INDEX idx_health_snapshot_at ON provider_health_snapshots(snapshot_at DESC);
CREATE INDEX idx_health_provider_time ON provider_health_snapshots(provider_id, snapshot_at DESC);

-- ============================================
-- COMMENTS (Documentation)
-- ============================================
COMMENT ON TABLE providers IS 'Payment providers (gateways, processors, etc.)';
COMMENT ON COLUMN providers.provider_id IS 'Business identifier (e.g., stripe, paypal)';
COMMENT ON COLUMN providers.sla_latency_ms IS 'P95 latency SLA in milliseconds';
COMMENT ON COLUMN providers.sla_success_rate IS 'Success rate SLA percentage';

COMMENT ON TABLE provider_health_snapshots IS 'Provider health metrics snapshots';
COMMENT ON COLUMN provider_health_snapshots.health_status IS 'HEALTHY, DEGRADED, or CRITICAL';
COMMENT ON COLUMN provider_health_snapshots.uptime_status IS 'UP, DEGRADED, or DOWN';
COMMENT ON COLUMN provider_health_snapshots.sla_compliance IS 'Overall SLA compliance percentage';

