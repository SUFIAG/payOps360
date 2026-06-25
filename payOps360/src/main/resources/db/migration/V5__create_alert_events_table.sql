-- V5: Create alert_events table

CREATE TABLE alert_events (
    id UUID PRIMARY KEY,
    alert_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    performed_by VARCHAR(100),
    notes TEXT,
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_alert_events_alert FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_alert_events_alert_id ON alert_events(alert_id);
CREATE INDEX idx_alert_events_occurred_at ON alert_events(occurred_at DESC);

-- Comments
COMMENT ON TABLE alert_events IS 'Alert state transition history';
COMMENT ON COLUMN alert_events.event_type IS 'Event type: CREATED, ACKNOWLEDGED, INVESTIGATING, RESOLVED, ESCALATED';

