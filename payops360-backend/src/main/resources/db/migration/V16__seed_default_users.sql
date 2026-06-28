-- ============================================
-- V16: Seed Default Users
-- ============================================
-- Creates default admin and test users for development/testing
-- Passwords are BCrypt hashed (12 rounds)

-- Default Organization (if not exists from V15)
INSERT INTO organizations (id, name, tier, status, created_at, updated_at)
VALUES (1, 'PayOps360 Default', 'PROFESSIONAL', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Admin User
-- Email: admin@payops360.com
-- Password: Admin@123
INSERT INTO users (
    organization_id, email, password_hash, first_name, last_name,
    status, two_factor_enabled, failed_login_attempts,
    password_expired, must_change_password,
    created_at, updated_at
) VALUES (
    1,
    'admin@payops360.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5c4jN6LV5nL0u', -- Admin@123
    'Admin',
    'User',
    'ACTIVE',
    false,
    0,
    false,
    false,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Admin Roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE email = 'admin@payops360.com'
ON CONFLICT DO NOTHING;

-- Test User 1 (Operations Manager)
-- Email: ops@payops360.com
-- Password: Ops@123
INSERT INTO users (
    organization_id, email, password_hash, first_name, last_name,
    status, two_factor_enabled, failed_login_attempts,
    password_expired, must_change_password,
    created_at, updated_at
) VALUES (
    1,
    'ops@payops360.com',
    '$2a$12$8ZnVzD6fKy5F4n0ZiZ6jJ.LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQ', -- Ops@123
    'Operations',
    'Manager',
    'ACTIVE',
    false,
    0,
    false,
    false,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Operations Manager Roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'OPERATIONS_MANAGER' FROM users WHERE email = 'ops@payops360.com'
ON CONFLICT DO NOTHING;

-- Test User 2 (Analyst)
-- Email: analyst@payops360.com
-- Password: Analyst@123
INSERT INTO users (
    organization_id, email, password_hash, first_name, last_name,
    status, two_factor_enabled, failed_login_attempts,
    password_expired, must_change_password,
    created_at, updated_at
) VALUES (
    1,
    'analyst@payops360.com',
    '$2a$12$9LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5c4jN6LV5nM', -- Analyst@123
    'Data',
    'Analyst',
    'ACTIVE',
    false,
    0,
    false,
    false,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Analyst Roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'ANALYST' FROM users WHERE email = 'analyst@payops360.com'
ON CONFLICT DO NOTHING;

-- Test User 3 (Support)
-- Email: support@payops360.com
-- Password: Support@123
INSERT INTO users (
    organization_id, email, password_hash, first_name, last_name,
    status, two_factor_enabled, failed_login_attempts,
    password_expired, must_change_password,
    created_at, updated_at
) VALUES (
    1,
    'support@payops360.com',
    '$2a$12$9LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5c4jN6LV5nS', -- Support@123
    'Support',
    'Agent',
    'ACTIVE',
    false,
    0,
    false,
    false,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Support Roles
INSERT INTO user_roles (user_id, role)
SELECT id, 'SUPPORT' FROM users WHERE email = 'support@payops360.com'
ON CONFLICT DO NOTHING;

-- Log seed completion
DO $$
BEGIN
    RAISE NOTICE 'Default users seeded successfully';
    RAISE NOTICE 'Admin: admin@payops360.com / Admin@123';
    RAISE NOTICE 'Operations Manager: ops@payops360.com / Ops@123';
    RAISE NOTICE 'Analyst: analyst@payops360.com / Analyst@123';
    RAISE NOTICE 'Support: support@payops360.com / Support@123';
END $$;

