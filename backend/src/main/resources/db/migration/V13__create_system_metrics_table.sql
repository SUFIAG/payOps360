-- V13: Create system_metrics table

CREATE TABLE system_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_timestamp TIMESTAMP NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    metric_unit VARCHAR(20),
    metric_category VARCHAR(50) NOT NULL, -- PERFORMANCE, THROUGHPUT, AVAILABILITY, RESOURCE
    tags JSONB,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_system_metrics_timestamp ON system_metrics(metric_timestamp DESC);
CREATE INDEX idx_system_metrics_name ON system_metrics(metric_name);
CREATE INDEX idx_system_metrics_category ON system_metrics(metric_category);
CREATE INDEX idx_system_metrics_composite ON system_metrics(metric_name, metric_timestamp DESC);

-- Partition by time for better performance (optional, for high-volume systems)
-- CREATE INDEX idx_system_metrics_recorded_at ON system_metrics(recorded_at DESC);

-- Comments
COMMENT ON TABLE system_metrics IS 'System-wide operational metrics for monitoring and analytics';
COMMENT ON COLUMN system_metrics.metric_category IS 'Category: PERFORMANCE, THROUGHPUT, AVAILABILITY, RESOURCE, BUSINESS';
COMMENT ON COLUMN system_metrics.tags IS 'Additional metadata as key-value pairs';

-- Common metrics that will be stored:
-- - api_latency_ms (PERFORMANCE)
-- - api_requests_per_second (THROUGHPUT)
-- - payment_success_rate (BUSINESS)
-- - incident_count (AVAILABILITY)
-- - jvm_memory_used_mb (RESOURCE)
-- - database_connections (RESOURCE)
-- - cache_hit_rate (PERFORMANCE)

