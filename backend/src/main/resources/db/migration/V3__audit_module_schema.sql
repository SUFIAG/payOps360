-- PayOps360 Database Schema - Version 3
-- Audit Module

-- ============================================
-- AUDIT LOGS TABLE
-- ============================================
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,

    -- Action Details
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,

    -- Actor Information
    user_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,

    -- Change Details
    old_values JSONB,
    new_values JSONB,

    -- Context
    request_id VARCHAR(255),
    session_id VARCHAR(255),

    -- Timestamp
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for audit logs
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_occurred ON audit_logs(occurred_at DESC);
CREATE INDEX idx_audit_user ON audit_logs(user_id);

-- ============================================
-- COMMENTS (Documentation)
-- ============================================
COMMENT ON TABLE audit_logs IS 'Complete audit trail of all system actions';
COMMENT ON COLUMN audit_logs.action IS 'Type of action performed';
COMMENT ON COLUMN audit_logs.entity_type IS 'Type of entity affected (Payment, Provider, etc.)';
COMMENT ON COLUMN audit_logs.entity_id IS 'Business identifier of the affected entity';
COMMENT ON COLUMN audit_logs.old_values IS 'Previous values before change (JSON)';
COMMENT ON COLUMN audit_logs.new_values IS 'New values after change (JSON)';

