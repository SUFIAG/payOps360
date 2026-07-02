-- PayOps360 Database Schema - Version 1
-- Initial schema for Phase 1: Payment Module

-- ============================================
-- PAYMENTS TABLE
-- ============================================
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(255) UNIQUE NOT NULL,
    external_transaction_id VARCHAR(255),
    merchant_reference VARCHAR(255),

    -- Financial Information
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    -- Provider Information
    provider_id VARCHAR(100) NOT NULL,
    provider_name VARCHAR(255),
    provider_transaction_id VARCHAR(255),

    -- Customer Information
    customer_id VARCHAR(255) NOT NULL,
    payment_method_type VARCHAR(50),
    payment_method_last4 VARCHAR(4),

    -- Status & Lifecycle
    status VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50),
    status_changed_at TIMESTAMPTZ,

    -- Tracking
    retry_count INT DEFAULT 0,
    is_stuck BOOLEAN DEFAULT FALSE,
    stuck_detected_at TIMESTAMPTZ,

    -- Metadata (JSONB for PostgreSQL)
    metadata JSONB,
    tags JSONB,

    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for payments table
CREATE INDEX idx_payment_payment_id ON payments(payment_id);
CREATE INDEX idx_payment_status ON payments(status);
CREATE INDEX idx_payment_provider ON payments(provider_id);
CREATE INDEX idx_payment_customer ON payments(customer_id);
CREATE INDEX idx_payment_created ON payments(created_at DESC);
CREATE INDEX idx_payment_stuck ON payments(is_stuck, status) WHERE is_stuck = TRUE;
CREATE INDEX idx_payment_external_txn ON payments(external_transaction_id);

-- ============================================
-- PAYMENT EVENTS TABLE
-- ============================================
CREATE TABLE payment_events (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,

    -- Event Details
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    reason TEXT,
    error_code VARCHAR(100),
    error_message TEXT,

    -- Timing
    duration_ms BIGINT,

    -- Metadata
    metadata JSONB,

    -- Timestamp
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for payment events
CREATE INDEX idx_event_payment ON payment_events(payment_id);
CREATE INDEX idx_event_occurred ON payment_events(occurred_at DESC);

-- ============================================
-- COMMENTS (Documentation)
-- ============================================
COMMENT ON TABLE payments IS 'Core payments table tracking payment lifecycle';
COMMENT ON COLUMN payments.payment_id IS 'Business identifier for payment (unique)';
COMMENT ON COLUMN payments.status IS 'Current payment status in lifecycle';
COMMENT ON COLUMN payments.is_stuck IS 'Flag indicating payment is stuck in current state';
COMMENT ON COLUMN payments.metadata IS 'Additional flexible data stored as JSON';

COMMENT ON TABLE payment_events IS 'Payment lifecycle events and state transitions';
COMMENT ON COLUMN payment_events.occurred_at IS 'Timestamp when the event occurred';

