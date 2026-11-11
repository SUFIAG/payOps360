-- =====================================================================
-- Phase 2: notification channels and routing, escalation policies,
-- incident SLA timers and postmortems.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Notification channels. Targets (webhook URLs, routing keys, address
-- lists) and signing secrets are AES-256-GCM encrypted by the application;
-- target_display holds a masked rendition safe to return from the API.
-- ---------------------------------------------------------------------
CREATE TABLE notification_channels (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID          NOT NULL UNIQUE,
    organization_id   BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name              VARCHAR(120)  NOT NULL,
    type              VARCHAR(20)   NOT NULL
        CHECK (type IN ('EMAIL', 'SLACK', 'MS_TEAMS', 'WEBHOOK', 'PAGERDUTY')),
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    target_ciphertext TEXT          NOT NULL,
    secret_ciphertext TEXT,
    target_display    VARCHAR(600)  NOT NULL,
    created_by        VARCHAR(320),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_channels_name UNIQUE (organization_id, name)
);
CREATE INDEX ix_notification_channels_org ON notification_channels (organization_id);

-- ---------------------------------------------------------------------
-- Routing rules: event type + minimum severity (+ optional provider) ->
-- channels. channel_ids reference notification_channels.id of the same tenant.
-- ---------------------------------------------------------------------
CREATE TABLE notification_rules (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID          NOT NULL UNIQUE,
    organization_id  BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             VARCHAR(120)  NOT NULL,
    event_type       VARCHAR(40)   NOT NULL
        CHECK (event_type IN ('ALERT_RAISED', 'INCIDENT_OPENED', 'INCIDENT_ESCALATED', 'INCIDENT_SLA_BREACHED',
                              'INCIDENT_RESOLVED', 'PROVIDER_DEGRADED')),
    min_severity     VARCHAR(20)   NOT NULL CHECK (min_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    provider_code    VARCHAR(60),
    channel_ids      BIGINT[]      NOT NULL DEFAULT '{}',
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_rules_org ON notification_rules (organization_id);
-- Hot path: routing an event looks up the enabled rules of one tenant and event type.
CREATE INDEX ix_notification_rules_routing ON notification_rules (organization_id, event_type) WHERE enabled;
-- Channel deletion removes the channel from every rule.
CREATE INDEX ix_notification_rules_channels ON notification_rules USING GIN (channel_ids);

-- Per-rule quiet window: the same event key is delivered at most once per window.
CREATE TABLE notification_dedup (
    rule_id      BIGINT        NOT NULL REFERENCES notification_rules (id) ON DELETE CASCADE,
    event_key    VARCHAR(200)  NOT NULL,
    last_sent_at TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (rule_id, event_key)
);
CREATE INDEX ix_notification_dedup_last_sent ON notification_dedup (last_sent_at);

-- ---------------------------------------------------------------------
-- Escalation policies: ordered levels; level N+1 is notified when the
-- subject is still unacknowledged delay_minutes after level N (level 1:
-- after it opened).
-- ---------------------------------------------------------------------
CREATE TABLE escalation_policies (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID          NOT NULL UNIQUE,
    organization_id  BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             VARCHAR(120)  NOT NULL,
    min_severity     VARCHAR(20)   NOT NULL CHECK (min_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_escalation_policies_name UNIQUE (organization_id, name)
);
CREATE INDEX ix_escalation_policies_org ON escalation_policies (organization_id);
CREATE INDEX ix_escalation_policies_enabled ON escalation_policies (organization_id) WHERE enabled;

CREATE TABLE escalation_levels (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id      BIGINT    NOT NULL REFERENCES escalation_policies (id) ON DELETE CASCADE,
    level_no       INT       NOT NULL CHECK (level_no BETWEEN 1 AND 10),
    delay_minutes  INT       NOT NULL CHECK (delay_minutes BETWEEN 0 AND 1440),
    channel_ids    BIGINT[]  NOT NULL DEFAULT '{}',
    CONSTRAINT uq_escalation_levels UNIQUE (policy_id, level_no)
);
CREATE INDEX ix_escalation_levels_channels ON escalation_levels USING GIN (channel_ids);

-- How far each unacknowledged incident / critical alert has been escalated.
CREATE TABLE escalation_progress (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id    BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    subject_type       VARCHAR(20)  NOT NULL CHECK (subject_type IN ('INCIDENT', 'ALERT')),
    subject_reference  VARCHAR(60)  NOT NULL,
    policy_id          BIGINT       NOT NULL REFERENCES escalation_policies (id) ON DELETE CASCADE,
    level              INT          NOT NULL,
    last_escalated_at  TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_escalation_progress_subject UNIQUE (organization_id, subject_type, subject_reference)
);
CREATE INDEX ix_escalation_progress_policy ON escalation_progress (policy_id);

-- ---------------------------------------------------------------------
-- Generic delivery outbox for every channel type. Rows are written in the
-- routing transaction and delivered by a ShedLock-guarded job with
-- exponential back-off (max 5 attempts).
-- ---------------------------------------------------------------------
CREATE TABLE notification_deliveries (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          UUID          NOT NULL UNIQUE,
    organization_id    BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    channel_id         BIGINT        REFERENCES notification_channels (id) ON DELETE SET NULL,
    channel_name       VARCHAR(120)  NOT NULL,
    channel_type       VARCHAR(20)   NOT NULL,
    rule_id            BIGINT        REFERENCES notification_rules (id) ON DELETE SET NULL,
    source             VARCHAR(20)   NOT NULL CHECK (source IN ('RULE', 'ESCALATION', 'TEST')),
    event_type         VARCHAR(40)   NOT NULL,
    event_key          VARCHAR(200),
    severity           VARCHAR(20),
    subject_type       VARCHAR(20),
    subject_reference  VARCHAR(60),
    title              VARCHAR(300)  NOT NULL,
    body               TEXT          NOT NULL,
    link               VARCHAR(500),
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts           INT           NOT NULL DEFAULT 0,
    last_error         VARCHAR(1000),
    next_attempt_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at            TIMESTAMPTZ
);
CREATE INDEX ix_notification_deliveries_due ON notification_deliveries (status, next_attempt_at);
CREATE INDEX ix_notification_deliveries_org_created ON notification_deliveries (organization_id, created_at DESC);
CREATE INDEX ix_notification_deliveries_org_status ON notification_deliveries (organization_id, status, created_at DESC);
CREATE INDEX ix_notification_deliveries_channel ON notification_deliveries (channel_id);
CREATE INDEX ix_notification_deliveries_rule ON notification_deliveries (rule_id);

-- ---------------------------------------------------------------------
-- Incident SLA timers. Targets are per tenant and severity; a tenant
-- without rows uses the built-in defaults (CRITICAL 5m/60m, HIGH 15m/4h,
-- MEDIUM 60m/24h, LOW 4h/72h).
-- ---------------------------------------------------------------------
CREATE TABLE incident_sla_policies (
    organization_id  BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    severity         VARCHAR(20)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ack_minutes      INT          NOT NULL CHECK (ack_minutes BETWEEN 1 AND 10080),
    resolve_minutes  INT          NOT NULL CHECK (resolve_minutes BETWEEN 1 AND 43200),
    updated_by       VARCHAR(320),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, severity),
    CHECK (resolve_minutes >= ack_minutes)
);

ALTER TABLE incidents
    ADD COLUMN ack_due_at          TIMESTAMPTZ,
    ADD COLUMN resolve_due_at      TIMESTAMPTZ,
    ADD COLUMN ack_breached_at     TIMESTAMPTZ,
    ADD COLUMN resolve_breached_at TIMESTAMPTZ,
    ADD COLUMN escalation_level    INT NOT NULL DEFAULT 0;

-- Back-fill existing incidents with the default targets; historical breaches
-- are recorded at their due time so SLA attainment statistics are accurate
-- and no notification storm is triggered for old incidents.
UPDATE incidents SET
    ack_due_at = opened_at + CASE severity
        WHEN 'CRITICAL' THEN INTERVAL '5 minutes' WHEN 'HIGH' THEN INTERVAL '15 minutes'
        WHEN 'MEDIUM' THEN INTERVAL '60 minutes' ELSE INTERVAL '4 hours' END,
    resolve_due_at = opened_at + CASE severity
        WHEN 'CRITICAL' THEN INTERVAL '60 minutes' WHEN 'HIGH' THEN INTERVAL '4 hours'
        WHEN 'MEDIUM' THEN INTERVAL '24 hours' ELSE INTERVAL '72 hours' END;
UPDATE incidents SET ack_breached_at = ack_due_at WHERE coalesce(acknowledged_at, now()) > ack_due_at;
UPDATE incidents SET resolve_breached_at = resolve_due_at WHERE coalesce(resolved_at, now()) > resolve_due_at;

-- SLA job: open incidents whose acknowledgement / resolution deadline passed and is not yet flagged.
CREATE INDEX ix_incidents_ack_due ON incidents (status, ack_due_at)
    WHERE status = 'OPEN' AND ack_breached_at IS NULL;
CREATE INDEX ix_incidents_resolve_due ON incidents (status, resolve_due_at)
    WHERE status <> 'RESOLVED' AND resolve_breached_at IS NULL;
-- Escalation job: unacknowledged incidents, oldest first.
CREATE INDEX ix_incidents_awaiting_ack ON incidents (opened_at) WHERE status = 'OPEN';
-- Escalation job: unacknowledged (critical) alerts.
CREATE INDEX ix_alerts_awaiting_ack ON alerts (severity, detected_at) WHERE status = 'OPEN';

-- ---------------------------------------------------------------------
-- Postmortems (one per resolved incident).
-- ---------------------------------------------------------------------
CREATE TABLE incident_postmortems (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id      BIGINT        NOT NULL UNIQUE REFERENCES incidents (id) ON DELETE CASCADE,
    organization_id  BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    summary          TEXT,
    impact           TEXT,
    root_cause       TEXT,
    timeline         JSONB         NOT NULL DEFAULT '[]'::jsonb,
    action_items     JSONB         NOT NULL DEFAULT '[]'::jsonb,
    ai_engine        VARCHAR(60),
    created_by       VARCHAR(320),
    updated_by       VARCHAR(320),
    published_by     VARCHAR(320),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_incident_postmortems_org_status ON incident_postmortems (organization_id, status, updated_at DESC);
