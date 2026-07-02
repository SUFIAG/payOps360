-- V15: Create User Management and Multi-Tenancy Tables
-- CRITICAL: This enables secure authentication, role-based access control, and multi-tenant data isolation

-- Organizations table (Multi-tenancy)
CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(100) NOT NULL UNIQUE, -- e.g., "acme-corp"
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_SETUP',
    tier VARCHAR(50) NOT NULL DEFAULT 'STARTER',
    settings JSONB, -- OrganizationSettings as JSON
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255)
);

CREATE INDEX idx_organizations_domain ON organizations(domain);
CREATE INDEX idx_organizations_status ON organizations(status);

-- Users table (Authentication & Authorization)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,

    -- Identity
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_INVITATION',

    -- Security (2FA)
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_secret VARCHAR(255),
    backup_codes JSONB, -- Array of recovery codes

    -- Session management
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45), -- IPv6 support
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,

    -- Password management
    password_changed_at TIMESTAMP,
    password_expired BOOLEAN NOT NULL DEFAULT FALSE,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE, -- Force change on first login

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invited_by VARCHAR(255),
    invitation_accepted_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_organization_id ON users(organization_id);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_last_login ON users(last_login_at DESC);

-- User roles (Many-to-many relationship)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role);

-- User invitations (Invite-only registration)
CREATE TABLE user_invitations (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,

    -- Invitation details
    email VARCHAR(255) NOT NULL,
    roles JSONB NOT NULL, -- Array of roles

    -- Security
    token VARCHAR(255) NOT NULL UNIQUE, -- UUID-based secure token
    expires_at TIMESTAMP NOT NULL, -- Default 7 days
    used BOOLEAN NOT NULL DEFAULT FALSE,

    -- Metadata
    invited_by VARCHAR(255) NOT NULL,
    invited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    accepted_from_ip VARCHAR(45)
);

CREATE INDEX idx_user_invitations_token ON user_invitations(token);
CREATE INDEX idx_user_invitations_email ON user_invitations(email);
CREATE INDEX idx_user_invitations_org_id ON user_invitations(organization_id);
CREATE INDEX idx_user_invitations_expires_at ON user_invitations(expires_at);

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_organizations_updated_at BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE organizations IS 'Multi-tenant organizations for data isolation';
COMMENT ON TABLE users IS 'User accounts with security-first design (JWT auth, 2FA ready)';
COMMENT ON TABLE user_roles IS 'Role-based access control (RBAC) junction table';
COMMENT ON TABLE user_invitations IS 'Invite-only user registration system';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password - NEVER store plain text';
COMMENT ON COLUMN users.two_factor_secret IS 'TOTP secret for 2FA authentication';
COMMENT ON COLUMN user_invitations.token IS 'Single-use, time-sensitive invitation token';

