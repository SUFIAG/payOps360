-- V12: Create anomaly_events table

CREATE TABLE anomaly_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anomaly_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL, -- PAYMENT, PROVIDER, SYSTEM
    entity_id VARCHAR(100),
    metric_name VARCHAR(100) NOT NULL,
    expected_value DOUBLE PRECISION NOT NULL,
    actual_value DOUBLE PRECISION NOT NULL,
    deviation_percentage DOUBLE PRECISION NOT NULL,
    severity VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    detection_algorithm VARCHAR(50) NOT NULL,
    confidence_score DOUBLE PRECISION,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    is_false_positive BOOLEAN DEFAULT false,
    metadata JSONB
);

-- Indexes for performance
CREATE INDEX idx_anomaly_entity ON anomaly_events(entity_type, entity_id);
CREATE INDEX idx_anomaly_detected ON anomaly_events(detected_at DESC);
CREATE INDEX idx_anomaly_severity ON anomaly_events(severity);
CREATE INDEX idx_anomaly_type ON anomaly_events(anomaly_type);
CREATE INDEX idx_anomaly_unresolved ON anomaly_events(resolved_at) WHERE resolved_at IS NULL;

-- Comments
COMMENT ON TABLE anomaly_events IS 'Detected anomalies in payment operations metrics';
COMMENT ON COLUMN anomaly_events.anomaly_type IS 'Type: SPIKE, DROP, TREND_CHANGE, OUTLIER, PATTERN_BREAK';
COMMENT ON COLUMN anomaly_events.detection_algorithm IS 'Algorithm used: STATISTICAL, ML, RULE_BASED';
COMMENT ON COLUMN anomaly_events.confidence_score IS 'Confidence in anomaly detection (0.0 to 1.0)';

