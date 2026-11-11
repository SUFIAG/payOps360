-- =====================================================================
-- PayOps360 baseline schema
-- Conventions:
--   * BIGINT identity primary keys for internal joins, UUID public ids
--     wherever an identifier leaves the system.
--   * Every business table carries organization_id (tenant isolation).
--   * All timestamps are TIMESTAMPTZ (UTC).
--   * Money is NUMERIC(19,4) + ISO-4217 currency code.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------
CREATE TABLE organizations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    mfa_required    BOOLEAN      NOT NULL DEFAULT TRUE,
    ai_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    base_currency   VARCHAR(3)   NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID         NOT NULL UNIQUE,
    organization_id        BIGINT       NOT NULL REFERENCES organizations (id),
    email                  VARCHAR(320) NOT NULL,
    full_name              VARCHAR(200) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    role                   VARCHAR(40)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    must_change_password   BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    mfa_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret_enc         TEXT,
    mfa_last_used_step     BIGINT,
    last_login_at          TIMESTAMPTZ,
    last_login_ip          VARCHAR(64),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_org ON users (organization_id);

CREATE TABLE user_password_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_pwd_history_user ON user_password_history (user_id, created_at DESC);

CREATE TABLE mfa_recovery_codes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_recovery_user ON mfa_recovery_codes (user_id);

-- Refresh-token sessions. Tokens are opaque and stored only as SHA-256 hashes.
-- family_id groups rotated tokens so a replayed token revokes the whole chain.
CREATE TABLE user_sessions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID         NOT NULL UNIQUE,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id           UUID         NOT NULL,
    refresh_token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(512),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoked_reason      VARCHAR(60)
);
CREATE INDEX ix_sessions_user ON user_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_sessions_family ON user_sessions (family_id);

CREATE TABLE login_attempts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        VARCHAR(320) NOT NULL,
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(512),
    success      BOOLEAN      NOT NULL,
    reason       VARCHAR(60),
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_attempts_email ON login_attempts (lower(email), attempted_at DESC);
CREATE INDEX ix_login_attempts_ip ON login_attempts (ip_address, attempted_at DESC);

-- Tamper-evident audit trail: each row hashes its content plus the previous hash.
CREATE TABLE audit_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  BIGINT       REFERENCES organizations (id),
    actor_user_id    BIGINT,
    actor_email      VARCHAR(320),
    action           VARCHAR(60)  NOT NULL,
    resource_type    VARCHAR(60),
    resource_id      VARCHAR(100),
    outcome          VARCHAR(20)  NOT NULL,
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(512),
    details          JSONB,
    previous_hash    VARCHAR(64),
    entry_hash       VARCHAR(64)  NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_org_time ON audit_logs (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_action ON audit_logs (action);

-- ---------------------------------------------------------------------
-- Payment providers
-- ---------------------------------------------------------------------
CREATE TABLE providers (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id   BIGINT        NOT NULL REFERENCES organizations (id),
    code              VARCHAR(60)   NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    type              VARCHAR(40)   NOT NULL,
    region            VARCHAR(60),
    sla_latency_ms    INT           NOT NULL DEFAULT 1500,
    sla_success_rate  NUMERIC(5,2)  NOT NULL DEFAULT 97.00,
    cost_bps          NUMERIC(7,2)  NOT NULL DEFAULT 0,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_provider_code UNIQUE (organization_id, code)
);

CREATE TABLE provider_health_snapshots (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id      BIGINT        NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    window_start     TIMESTAMPTZ   NOT NULL,
    window_end       TIMESTAMPTZ   NOT NULL,
    total_count      INT           NOT NULL,
    success_count    INT           NOT NULL,
    failure_count    INT           NOT NULL,
    success_rate     NUMERIC(5,2)  NOT NULL,
    avg_latency_ms   INT           NOT NULL,
    p95_latency_ms   INT           NOT NULL,
    health_status    VARCHAR(20)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_health_provider_time ON provider_health_snapshots (provider_id, window_end DESC);

-- ---------------------------------------------------------------------
-- Payments
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id     BIGINT        NOT NULL REFERENCES organizations (id),
    reference           VARCHAR(40)   NOT NULL UNIQUE,
    idempotency_key     VARCHAR(100),
    merchant_reference  VARCHAR(100),
    amount              NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    currency            VARCHAR(3)    NOT NULL,
    provider_id         BIGINT        NOT NULL REFERENCES providers (id),
    customer_ref        VARCHAR(100)  NOT NULL,
    method              VARCHAR(30)   NOT NULL,
    card_last4          VARCHAR(4),
    country             VARCHAR(2),
    status              VARCHAR(30)   NOT NULL,
    previous_status     VARCHAR(30),
    status_changed_at   TIMESTAMPTZ   NOT NULL,
    failure_code        VARCHAR(60),
    failure_message     VARCHAR(500),
    latency_ms          INT,
    retry_count         INT           NOT NULL DEFAULT 0,
    stuck               BOOLEAN       NOT NULL DEFAULT FALSE,
    risk_score          INT           NOT NULL DEFAULT 0,
    risk_level          VARCHAR(10)   NOT NULL DEFAULT 'LOW',
    risk_factors        JSONB         NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_payment_idempotency UNIQUE (organization_id, idempotency_key)
);
CREATE INDEX ix_payments_org_created ON payments (organization_id, created_at DESC);
CREATE INDEX ix_payments_org_status ON payments (organization_id, status);
CREATE INDEX ix_payments_provider_created ON payments (provider_id, created_at DESC);
CREATE INDEX ix_payments_customer ON payments (organization_id, customer_ref, created_at DESC);
CREATE INDEX ix_payments_stuck ON payments (organization_id) WHERE stuck;

CREATE TABLE payment_events (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id    BIGINT       NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30)  NOT NULL,
    reason        VARCHAR(500),
    error_code    VARCHAR(60),
    actor         VARCHAR(320),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_events_payment ON payment_events (payment_id, occurred_at);

-- ---------------------------------------------------------------------
-- Alerts & incidents
-- ---------------------------------------------------------------------
CREATE TABLE incidents (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id    BIGINT        NOT NULL REFERENCES organizations (id),
    reference          VARCHAR(40)   NOT NULL UNIQUE,
    title              VARCHAR(300)  NOT NULL,
    description        TEXT,
    severity           VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    provider_id        BIGINT        REFERENCES providers (id),
    affected_payments  INT           NOT NULL DEFAULT 0,
    affected_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    opened_at          TIMESTAMPTZ   NOT NULL,
    acknowledged_at    TIMESTAMPTZ,
    acknowledged_by    VARCHAR(320),
    resolved_at        TIMESTAMPTZ,
    resolved_by        VARCHAR(320),
    resolution_note    TEXT,
    ai_summary         TEXT,
    ai_root_cause      TEXT,
    ai_recommendations JSONB,
    ai_confidence      NUMERIC(4,3),
    ai_engine          VARCHAR(60),
    ai_generated_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_incidents_org_status ON incidents (organization_id, status, opened_at DESC);

CREATE TABLE incident_timeline (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id  BIGINT        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    kind         VARCHAR(30)   NOT NULL,
    message      VARCHAR(2000) NOT NULL,
    actor        VARCHAR(320),
    occurred_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_incident_timeline ON incident_timeline (incident_id, occurred_at);

CREATE TABLE alerts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  BIGINT        NOT NULL REFERENCES organizations (id),
    reference        VARCHAR(40)   NOT NULL UNIQUE,
    type             VARCHAR(40)   NOT NULL,
    severity         VARCHAR(20)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    source           VARCHAR(20)   NOT NULL,
    title            VARCHAR(300)  NOT NULL,
    description      TEXT,
    provider_id      BIGINT        REFERENCES providers (id),
    payment_reference VARCHAR(40),
    incident_id      BIGINT        REFERENCES incidents (id),
    metric_value     NUMERIC(19,4),
    threshold        NUMERIC(19,4),
    fingerprint      VARCHAR(200)  NOT NULL,
    detected_at      TIMESTAMPTZ   NOT NULL,
    last_seen_at     TIMESTAMPTZ   NOT NULL,
    acknowledged_at  TIMESTAMPTZ,
    acknowledged_by  VARCHAR(320),
    resolved_at      TIMESTAMPTZ,
    resolved_by      VARCHAR(320),
    resolution_note  TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_alerts_org_status ON alerts (organization_id, status, detected_at DESC);
CREATE INDEX ix_alerts_fingerprint ON alerts (organization_id, fingerprint) WHERE status <> 'RESOLVED';

-- ---------------------------------------------------------------------
-- AI usage ledger (governance: who used which AI feature, cost drivers)
-- ---------------------------------------------------------------------
CREATE TABLE ai_interactions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id   BIGINT       NOT NULL REFERENCES organizations (id),
    user_id           BIGINT,
    feature           VARCHAR(40)  NOT NULL,
    engine            VARCHAR(60)  NOT NULL,
    input_tokens      INT          NOT NULL DEFAULT 0,
    output_tokens     INT          NOT NULL DEFAULT 0,
    latency_ms        INT          NOT NULL DEFAULT 0,
    success           BOOLEAN      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_interactions_org ON ai_interactions (organization_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Scheduler lock (ShedLock) so jobs run once across replicas
-- ---------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
