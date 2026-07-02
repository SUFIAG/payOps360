-- PayOps360 Database Schema - Version 4
-- Phase 2: Alert Module

-- ============================================
-- ALERTS TABLE
-- ============================================
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(255) UNIQUE NOT NULL,
    
    -- Classification
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    
    -- Target Entity
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    
    -- Details
    title VARCHAR(500) NOT NULL,
    description TEXT,
    metric_name VARCHAR(100),
    metric_value DECIMAL(19,4),
    threshold_value DECIMAL(19,4),
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    status_changed_at TIMESTAMPTZ,
    
    -- Resolution
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(255),
    resolution_note TEXT,
    
    -- Incident Link
    incident_id BIGINT,
    
    -- Metadata
    metadata JSONB,
    
    -- Timestamps
    detected_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for alerts
CREATE INDEX idx_alert_alert_id ON alerts(alert_id);
CREATE INDEX idx_alert_status ON alerts(status, severity);
CREATE INDEX idx_alert_entity ON alerts(entity_type, entity_id);
CREATE INDEX idx_alert_detected ON alerts(detected_at DESC);
CREATE INDEX idx_alert_incident ON alerts(incident_id) WHERE incident_id IS NOT NULL;
CREATE INDEX idx_alert_active ON alerts(status) WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING');

-- ============================================
-- COMMENTS (Documentation)
-- ============================================
COMMENT ON TABLE alerts IS 'System alerts triggered by threshold breaches and anomalies';
COMMENT ON COLUMN alerts.alert_id IS 'Business identifier for alert';
COMMENT ON COLUMN alerts.alert_type IS 'Type of alert (PROVIDER_DOWN, HIGH_LATENCY, etc.)';
COMMENT ON COLUMN alerts.severity IS 'Alert severity (LOW, MEDIUM, HIGH, CRITICAL)';
COMMENT ON COLUMN alerts.status IS 'Alert lifecycle status (OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED, etc.)';
COMMENT ON COLUMN alerts.incident_id IS 'Link to incident if alert is correlated';
