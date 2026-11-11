# PayOps360 — Security Architecture

The controls below map to **OWASP ASVS 4.0 (L2)**, the **OWASP API Security Top 10** and **PCI DSS v4.0** requirements 3, 7, 8 and 10. Requirement 3 is satisfied by design: no card PAN, CVV or track data is ever accepted; card payments carry only the last 4 digits.

## Authentication

| Control | Implementation |
|---|---|
| Password storage | BCrypt cost 12. Passwords over 72 bytes are rejected, never silently truncated. |
| Password policy (NIST 800-63B, PCI 8.3.6) | Minimum 12 characters; upper, lower, digit and symbol; deny-list of common passwords; no email or name fragments; no 4+ repeated characters; history of last 5; 90-day expiry forces a change |
| Account provisioning | No public sign-up. Admins create users with a random 16-character temporary password. The API refuses all calls until the user changes it (`PASSWORD_CHANGE_REQUIRED`). |
| MFA | RFC 6238 TOTP (any authenticator app), ±1 step drift, **replay protection** (each 30 s step is usable once), 10 single-use recovery codes stored as SHA-256 hashes. TOTP seeds are encrypted at rest with **AES-256-GCM**. Organisations can make MFA mandatory; users are then forced to enrol at first sign-in. |
| Brute force | Progressive account lockout: 5 failures lock for 15 min, doubling per block up to 24 h. MFA failures count too. Per-IP ceiling of 30 failures per 15 min. Sliding-window rate limit of 10 auth requests per minute per IP. |
| Enumeration | Unknown user, wrong password, locked and disabled accounts all return the same message, with equalised BCrypt timing. |
| Login forensics | Every attempt is recorded (email, IP, user agent, outcome, reason). |

## Sessions and tokens

| Control | Implementation |
|---|---|
| Access token | HS512 JWT, **15 min** lifetime. `typ` claim prevents token confusion (an MFA-challenge token is never accepted as an access token). Bound to a server-side session id (`sid`) that is checked on every request (10 s cache), so revocation is near-immediate. |
| Refresh token | 256-bit opaque random value. Only its SHA-256 hash is stored. Delivered **only** in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`. Never readable by JavaScript and never in a response body. |
| Rotation and theft detection | Every refresh rotates the token. Presenting an already-rotated token revokes the **whole session family** and raises a `TOKEN_REUSE_DETECTED` audit event. A 10 s grace window absorbs benign multi-tab races. |
| Absolute lifetime | 12 h per session family; rotation never extends it. |
| CSRF | Cookie-authenticated endpoints (`/auth/refresh`, `/auth/logout`) require `X-Requested-With: payops`. Browsers cannot set this cross-site without a CORS pre-flight, and CORS is restricted to an allow-list. All other endpoints use Bearer tokens. |
| Revocation | Logout, password change (other devices), role change, disable, password or MFA reset all revoke sessions. Users can list and revoke their own sessions. |
| Front end | Access token kept **in memory only** (no localStorage). The session is restored from the HttpOnly cookie through a same-origin proxy. |

## Authorisation

- Five least-privilege roles mapped to 12 fine-grained permissions. Every endpoint uses `@PreAuthorize(hasAuthority(...))` on permissions, never on role names.
- Segregation of duties: only `ADMIN` manages users; `AUDITOR` is read-only plus the audit trail; admins cannot change their own role or status.
- **Tenant isolation:** every query is scoped by `organization_id` taken from the token, never from user input. The live event stream is partitioned per tenant.

## API and transport hardening

- Security headers on both API and web tier:
  - HSTS
  - CSP: `default-src 'none'` on the API; strict self-only on the web tier
  - X-Frame-Options DENY
  - nosniff
  - Referrer-Policy no-referrer
  - Permissions-Policy
  - COOP
  - Cache-Control no-store
- Bean Validation on every request DTO, with size limits on every string. Search inputs are escaped. SQL is fully parameterised.
- Errors return a stable code plus a correlation id. Stack traces and exception messages never leave the server.
- Idempotency keys on payment ingestion prevent duplicate payments on client retries.
- Optimistic locking (`@Version`) on aggregates prevents lost updates.
- Actuator: only health and info are public. Metrics require `USER_MANAGE`. Swagger is enabled in the dev profile only.
- Secrets come from the environment. Startup **fails** if `JWT_SECRET` or `DATA_ENCRYPTION_KEY` is missing, or if the dev JWT key is used outside the dev profile.

## Audit trail (PCI DSS 10)

- An append-only `audit_logs` table records:
  - login success, failure and block; lockouts; MFA events; token reuse;
  - password and role changes; user lifecycle;
  - payment status changes; provider changes; alert and incident actions;
  - AI investigations and copilot queries.
- **Tamper evidence:** each entry stores `SHA-256(previousHash | content)`. Appends are serialised per tenant with a PostgreSQL advisory lock. `GET /api/v1/audit/verify` re-computes the chain and reports the first broken entry.
- Audit writes run in their own transaction, so a rejected operation still leaves its trace.

## AI security and governance

- Generative AI is opt-in per tenant (`organizations.ai_enabled`) and runs only when an API key is configured.
- Copilot tools are **read-only** and tenant-scoped. Customer references are masked (`***1234`) before data leaves the platform. Card data never exists in the system.
- Prompt-injection guardrails:
  - Data is wrapped in `<data>` tags and declared untrusted.
  - Instructions inside data are ignored.
  - LLM structured outputs are re-validated against allow-lists before use (for example, smart-search filters).
- Every LLM call is recorded in `ai_interactions` (feature, engine, tokens, latency, success) and copilot queries are audited.
- Failure isolation: any LLM error falls back to the deterministic engine; AI never blocks payment ingestion.

## Production checklist

1. Set `JWT_SECRET` (≥ 512-bit random, base64), `DATA_ENCRYPTION_KEY` (256-bit, base64; ideally from a KMS or HSM), `DB_PASSWORD`, `ALLOWED_ORIGINS`.
2. Run with `SPRING_PROFILES_ACTIVE=prod` (no dev seeding or simulator, no Swagger).
3. Set `organizations.mfa_required = true` for every tenant (the default for new tenants).
4. Terminate TLS 1.2+ at the edge. Forward `X-Forwarded-*` only from trusted proxies.
5. Run multiple replicas behind a WAF. Move rate-limit buckets and the SSE fan-out to Redis.
6. Ship logs (with correlation ids) to the SIEM. Alert on `TOKEN_REUSE_DETECTED`, `ACCOUNT_LOCKED` and `LOGIN_BLOCKED`.
