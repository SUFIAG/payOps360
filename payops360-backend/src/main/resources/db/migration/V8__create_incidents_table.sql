-- V8: Create incidents table

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    affected_provider_id VARCHAR(100),
    affected_region VARCHAR(100),
    impacted_payment_count INTEGER NOT NULL,
    estimated_impact DOUBLE PRECISION NOT NULL,
    detected_at TIMESTAMP NOT NULL,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    assigned_to VARCHAR(100),
    root_cause TEXT,
    resolution TEXT,
    related_alert_ids JSONB,
    related_payment_ids JSONB
);

-- Indexes for performance
CREATE INDEX idx_incident_status ON incidents(status);
CREATE INDEX idx_incident_provider ON incidents(affected_provider_id);
CREATE INDEX idx_incident_severity ON incidents(severity);
CREATE INDEX idx_incident_detected_at ON incidents(detected_at DESC);
CREATE INDEX idx_incident_category ON incidents(category);

-- Comments
COMMENT ON TABLE incidents IS 'Correlated incidents from related alerts and failures';
COMMENT ON COLUMN incidents.severity IS 'Severity: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN incidents.status IS 'Status: OPEN, INVESTIGATING, RESOLVED';
COMMENT ON COLUMN incidents.category IS 'Category: PROVIDER_OUTAGE, NETWORK_ISSUE, PERFORMANCE_DEGRADATION, HIGH_FAILURE_RATE, FRAUD_SPIKE, SYSTEM_ERROR, UNKNOWN';
COMMENT ON COLUMN incidents.related_alert_ids IS 'JSON array of alert UUIDs that are part of this incident';
COMMENT ON COLUMN incidents.related_payment_ids IS 'JSON array of payment UUIDs affected by this incident';

