-- =====================================================================
-- Phase 3: settlement reconciliation — provider settlement files,
-- matching against the payment ledger, exception queue and fee leakage.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Ledger hooks. provider_reference holds the provider-side transaction id
-- when an integration records it; matching also accepts the PayOps
-- reference and the merchant reference.
-- ---------------------------------------------------------------------
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(100);
CREATE INDEX IF NOT EXISTS ix_payments_org_provider_ref ON payments (organization_id, provider_reference)
    WHERE provider_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_payments_org_merchant_ref ON payments (organization_id, merchant_reference)
    WHERE merchant_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_payments_org_status_created ON payments (organization_id, status, created_at DESC);

-- ---------------------------------------------------------------------
-- Per-provider CSV column mapping. columns maps a logical field
-- (reference, amount, currency, fee, fxRate, settledAt, type) to a header.
-- ---------------------------------------------------------------------
CREATE TABLE settlement_mapping_profiles (
    organization_id  BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    provider_id      BIGINT        NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    delimiter        VARCHAR(1)    NOT NULL DEFAULT ',',
    date_format      VARCHAR(40),
    columns          JSONB         NOT NULL,
    updated_by       VARCHAR(320),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, provider_id)
);

-- ---------------------------------------------------------------------
-- Expected fee schedule: percentage + fixed fee, optionally narrowed by
-- payment method and/or currency (most specific rule wins).
-- ---------------------------------------------------------------------
CREATE TABLE provider_fee_schedules (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    provider_id      BIGINT        NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    method           VARCHAR(30),
    currency         VARCHAR(3),
    percentage       NUMERIC(7,4)  NOT NULL CHECK (percentage >= 0),
    fixed_fee        NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (fixed_fee >= 0),
    updated_by       VARCHAR(320),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_fee_schedule_rule ON provider_fee_schedules
    (organization_id, provider_id, coalesce(method, ''), coalesce(currency, ''));

-- ---------------------------------------------------------------------
-- Settlement batches: one uploaded file. The SHA-256 checksum rejects
-- re-uploads of the same file within a tenant.
-- ---------------------------------------------------------------------
CREATE TABLE settlement_batches (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference             VARCHAR(40)   NOT NULL UNIQUE,
    organization_id       BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    provider_id           BIGINT        NOT NULL REFERENCES providers (id),
    file_name             VARCHAR(255)  NOT NULL,
    checksum              VARCHAR(80)   NOT NULL,
    status                VARCHAR(20)   NOT NULL
        CHECK (status IN ('RECEIVED', 'MATCHING', 'COMPLETED', 'FAILED')),
    total_lines           INT           NOT NULL DEFAULT 0,
    rejected_lines        INT           NOT NULL DEFAULT 0,
    processed_lines       INT           NOT NULL DEFAULT 0,
    matched_count         INT           NOT NULL DEFAULT 0,
    mismatch_count        INT           NOT NULL DEFAULT 0,
    missing_in_ledger     INT           NOT NULL DEFAULT 0,
    missing_in_settlement INT           NOT NULL DEFAULT 0,
    duplicate_count       INT           NOT NULL DEFAULT 0,
    fee_overcharge_count  INT           NOT NULL DEFAULT 0,
    exception_count       INT           NOT NULL DEFAULT 0,
    gross_amount_usd      NUMERIC(19,4) NOT NULL DEFAULT 0,
    fee_amount_usd        NUMERIC(19,4) NOT NULL DEFAULT 0,
    period_start          TIMESTAMPTZ,
    period_end            TIMESTAMPTZ,
    parse_errors          JSONB         NOT NULL DEFAULT '[]'::jsonb,
    error_message         VARCHAR(500),
    uploaded_by           VARCHAR(320),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_settlement_batches_checksum ON settlement_batches (organization_id, checksum);
CREATE INDEX ix_settlement_batches_org_created ON settlement_batches (organization_id, created_at DESC);
CREATE INDEX ix_settlement_batches_pending ON settlement_batches (status) WHERE status IN ('RECEIVED', 'MATCHING');

CREATE TABLE settlement_lines (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id            BIGINT        NOT NULL REFERENCES settlement_batches (id) ON DELETE CASCADE,
    organization_id     BIGINT        NOT NULL,
    line_no             INT           NOT NULL,
    type                VARCHAR(20)   NOT NULL CHECK (type IN ('SALE', 'REFUND', 'CHARGEBACK', 'FEE')),
    provider_reference  VARCHAR(100),
    amount              NUMERIC(19,4) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    fee                 NUMERIC(19,4),
    expected_fee        NUMERIC(19,4),
    fx_rate             NUMERIC(19,8),
    settled_at          TIMESTAMPTZ   NOT NULL,
    match_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (match_status IN ('PENDING', 'MATCHED', 'AMOUNT_MISMATCH', 'MISSING_IN_LEDGER', 'DUPLICATE', 'FEE_ONLY')),
    match_method        VARCHAR(10)   CHECK (match_method IN ('EXACT', 'FUZZY', 'MANUAL')),
    payment_id          BIGINT        REFERENCES payments (id)
);
CREATE INDEX ix_settlement_lines_batch ON settlement_lines (batch_id, id);
CREATE INDEX ix_settlement_lines_org_ref ON settlement_lines (organization_id, provider_reference);
CREATE INDEX ix_settlement_lines_payment ON settlement_lines (payment_id, type) WHERE payment_id IS NOT NULL;
CREATE INDEX ix_settlement_lines_org_settled ON settlement_lines (organization_id, settled_at);

-- ---------------------------------------------------------------------
-- Exception queue.
-- ---------------------------------------------------------------------
CREATE TABLE reconciliation_exceptions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference           VARCHAR(40)   NOT NULL UNIQUE,
    organization_id     BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    provider_id         BIGINT        NOT NULL REFERENCES providers (id),
    batch_id            BIGINT        REFERENCES settlement_batches (id) ON DELETE CASCADE,
    line_id             BIGINT        REFERENCES settlement_lines (id) ON DELETE CASCADE,
    payment_id          BIGINT        REFERENCES payments (id),
    type                VARCHAR(30)   NOT NULL
        CHECK (type IN ('AMOUNT_MISMATCH', 'MISSING_IN_SETTLEMENT', 'MISSING_IN_LEDGER', 'DUPLICATE', 'FEE_OVERCHARGE')),
    status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'WRITTEN_OFF')),
    amount_at_risk      NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency            VARCHAR(3)    NOT NULL,
    expected_amount     NUMERIC(19,4),
    actual_amount       NUMERIC(19,4),
    payment_reference   VARCHAR(40),
    provider_reference  VARCHAR(100),
    detail              VARCHAR(500),
    assignee            VARCHAR(320),
    resolution_reason   VARCHAR(40),
    resolution_note     VARCHAR(1000),
    resolved_by         VARCHAR(320),
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_recon_exceptions_org_status ON reconciliation_exceptions (organization_id, status, created_at DESC);
CREATE INDEX ix_recon_exceptions_batch ON reconciliation_exceptions (batch_id);
CREATE INDEX ix_recon_exceptions_payment ON reconciliation_exceptions (payment_id) WHERE payment_id IS NOT NULL;
-- A payment is flagged as missing from settlement at most once.
CREATE UNIQUE INDEX ux_recon_exceptions_missing ON reconciliation_exceptions (organization_id, payment_id)
    WHERE type = 'MISSING_IN_SETTLEMENT';

-- ---------------------------------------------------------------------
-- Notification routing: new operational event type.
-- ---------------------------------------------------------------------
ALTER TABLE notification_rules DROP CONSTRAINT IF EXISTS notification_rules_event_type_check;
ALTER TABLE notification_rules ADD CONSTRAINT ck_notification_rules_event_type
    CHECK (event_type IN ('ALERT_RAISED', 'INCIDENT_OPENED', 'INCIDENT_ESCALATED', 'INCIDENT_SLA_BREACHED',
                          'INCIDENT_RESOLVED', 'PROVIDER_DEGRADED', 'RECONCILIATION_EXCEPTIONS'));
