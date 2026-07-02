-- V7: Create retry_strategies table

CREATE TABLE retry_strategies (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    failure_category VARCHAR(50) NOT NULL,
    strategy_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_attempts INTEGER NOT NULL,
    delay_millis BIGINT NOT NULL,
    fallback_provider_id VARCHAR(100),
    recommendation VARCHAR(500) NOT NULL,
    reasoning TEXT NOT NULL,
    recommended_at TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    current_attempt INTEGER,
    successful BOOLEAN NOT NULL DEFAULT false
);

-- Indexes for performance
CREATE INDEX idx_retry_payment_id ON retry_strategies(payment_id);
CREATE INDEX idx_retry_status ON retry_strategies(status);
CREATE INDEX idx_retry_failure_category ON retry_strategies(failure_category);
CREATE INDEX idx_retry_recommended_at ON retry_strategies(recommended_at DESC);

-- Comments
COMMENT ON TABLE retry_strategies IS 'Intelligent retry strategy recommendations and execution tracking';
COMMENT ON COLUMN retry_strategies.strategy_type IS 'Type: IMMEDIATE, EXPONENTIAL_BACKOFF, FALLBACK_PROVIDER, MANUAL_INTERVENTION, BLOCK_RETRY';
COMMENT ON COLUMN retry_strategies.status IS 'Status: RECOMMENDED, EXECUTING, SUCCESS, FAILED, EXHAUSTED, BLOCKED';

