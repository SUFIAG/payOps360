-- =====================================================================
-- Phase 1: identity onboarding (password recovery, invitations,
-- self-service sign-up) and the transactional e-mail outbox.
-- =====================================================================

-- Users may now be INVITED (no usable password yet) or
-- PENDING_VERIFICATION (self-signup awaiting e-mail confirmation).
ALTER TABLE users
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'INVITED', 'PENDING_VERIFICATION'));

-- Single-use, short-lived tokens delivered by e-mail. Only the SHA-256 hash
-- of the 256-bit random token is stored; the raw value exists only in the link.
CREATE TABLE user_action_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(30) NOT NULL
        CHECK (purpose IN ('PASSWORD_RESET', 'INVITATION', 'EMAIL_VERIFICATION')),
    -- UNIQUE also provides the lookup index used to redeem a token.
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_action_tokens_user ON user_action_tokens (user_id, purpose, created_at DESC);

-- Transactional outbox: messages are written in the same transaction as the
-- business change and delivered asynchronously by a ShedLock-guarded job.
CREATE TABLE email_outbox (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID         NOT NULL UNIQUE,
    organization_id  BIGINT       REFERENCES organizations (id) ON DELETE SET NULL,
    recipient        VARCHAR(320) NOT NULL,
    subject          VARCHAR(300) NOT NULL,
    category         VARCHAR(40)  NOT NULL,
    text_body        TEXT,
    html_body        TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'LOGGED')),
    attempts         INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(1000),
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ
);
CREATE INDEX ix_email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_email_outbox_status_created ON email_outbox (status, created_at DESC);
CREATE INDEX ix_email_outbox_created ON email_outbox (created_at DESC);

-- Lookups by onboarding state (pending invitations / unverified sign-ups per tenant).
CREATE INDEX ix_users_org_status ON users (organization_id, status);
CREATE INDEX ix_users_status ON users (status);
