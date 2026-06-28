-- V11: Create provider_performance_metrics table

CREATE TABLE provider_performance_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id VARCHAR(100) NOT NULL,
    metric_timestamp TIMESTAMP NOT NULL,
    time_bucket VARCHAR(20) NOT NULL, -- HOURLY, DAILY, WEEKLY
    total_requests BIGINT NOT NULL DEFAULT 0,
    successful_requests BIGINT NOT NULL DEFAULT 0,
    failed_requests BIGINT NOT NULL DEFAULT 0,
    success_rate DOUBLE PRECISION NOT NULL,
    failure_rate DOUBLE PRECISION NOT NULL,
    average_latency_ms BIGINT NOT NULL,
    p95_latency_ms BIGINT,
    p99_latency_ms BIGINT,
    timeout_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    sla_compliance_rate DOUBLE PRECISION,
    uptime_percentage DOUBLE PRECISION
);

-- Indexes for performance
CREATE INDEX idx_provider_perf_provider ON provider_performance_metrics(provider_id);
CREATE INDEX idx_provider_perf_timestamp ON provider_performance_metrics(metric_timestamp DESC);
CREATE INDEX idx_provider_perf_bucket ON provider_performance_metrics(time_bucket);
CREATE INDEX idx_provider_perf_composite ON provider_performance_metrics(provider_id, metric_timestamp DESC);

-- Unique constraint to prevent duplicate metric entries
CREATE UNIQUE INDEX idx_provider_perf_unique ON provider_performance_metrics(provider_id, metric_timestamp, time_bucket);

-- Comments
COMMENT ON TABLE provider_performance_metrics IS 'Pre-aggregated provider performance metrics for fast analytics';
COMMENT ON COLUMN provider_performance_metrics.time_bucket IS 'Aggregation level: HOURLY, DAILY, WEEKLY';
COMMENT ON COLUMN provider_performance_metrics.success_rate IS 'Percentage of successful requests (0.0 to 1.0)';

