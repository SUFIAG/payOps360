-- V6: Create failure_classifications table

CREATE TABLE failure_classifications (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    failure_type VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    provider_id VARCHAR(100),
    severity VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    is_retryable BOOLEAN NOT NULL DEFAULT false,
    recommendation TEXT,
    reasoning TEXT,
    classified_at TIMESTAMP NOT NULL,
    metadata JSONB
);

-- Indexes for performance
CREATE INDEX idx_failure_payment_id ON failure_classifications(payment_id);
CREATE INDEX idx_failure_type ON failure_classifications(failure_type);
CREATE INDEX idx_failure_category ON failure_classifications(category);
CREATE INDEX idx_failure_provider ON failure_classifications(provider_id);
CREATE INDEX idx_failure_classified_at ON failure_classifications(classified_at DESC);

-- Comments
COMMENT ON TABLE failure_classifications IS 'Intelligent failure classification with recommendations';
COMMENT ON COLUMN failure_classifications.failure_type IS 'Type: NETWORK_FAILURE, PROVIDER_FAILURE, TIMEOUT, VALIDATION_ERROR, BUSINESS_RULE_BLOCK, INSUFFICIENT_FUNDS, DUPLICATE, FRAUD_BLOCK, UNKNOWN';
COMMENT ON COLUMN failure_classifications.severity IS 'Severity: CRITICAL, HIGH, MEDIUM, LOW';
COMMENT ON COLUMN failure_classifications.category IS 'Categorized failure reason';

