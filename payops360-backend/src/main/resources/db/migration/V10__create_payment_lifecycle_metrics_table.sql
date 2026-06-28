-- V10: Create payment_lifecycle_metrics table

CREATE TABLE payment_lifecycle_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    state_from VARCHAR(50),
    state_to VARCHAR(50) NOT NULL,
    duration_ms BIGINT NOT NULL,
    transition_timestamp TIMESTAMP NOT NULL,
    is_success BOOLEAN NOT NULL DEFAULT true,
    error_message TEXT,
    CONSTRAINT fk_lifecycle_metrics_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_lifecycle_metrics_payment ON payment_lifecycle_metrics(payment_id);
CREATE INDEX idx_lifecycle_metrics_state_to ON payment_lifecycle_metrics(state_to);
CREATE INDEX idx_lifecycle_metrics_timestamp ON payment_lifecycle_metrics(transition_timestamp DESC);
CREATE INDEX idx_lifecycle_metrics_duration ON payment_lifecycle_metrics(duration_ms);

-- Comments
COMMENT ON TABLE payment_lifecycle_metrics IS 'Tracks payment state transitions and durations for analytics';
COMMENT ON COLUMN payment_lifecycle_metrics.duration_ms IS 'Time spent in previous state (milliseconds)';
COMMENT ON COLUMN payment_lifecycle_metrics.is_success IS 'Whether the transition was successful';

