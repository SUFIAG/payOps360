# PayOps360 — AI-Native Payment Operations Command Center

PayOps360 gives payment operations teams one real-time view of every payment, provider and corridor. An AI layer detects anomalies, correlates incidents, explains root causes, scores risk, recommends routing and answers questions in plain language.

**Built for:**
- cross-border remittance operators;
- PSPs and payment facilitators;
- digital banks and EMIs;
- high-volume merchants.

See the [BRD](docs/BRD.md) for scope, target audience and differentiation.

| Doc | Contents |
|---|---|
| [docs/BRD.md](docs/BRD.md) | Problem, target customers and personas, scope, AI capabilities, NFRs, KPIs |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Hexagonal modular monolith, module map, ports, jobs, frontend structure |
| [docs/SECURITY.md](docs/SECURITY.md) | MFA, sessions, lockout, RBAC, audit chain, AI governance, production checklist |

## Tech stack

- **Backend:** Java 17, Spring Boot 3.5, Spring Security, JPA + JDBC, PostgreSQL, Flyway, ShedLock, jjwt, Anthropic Java SDK (Claude), ArchUnit
- **Frontend:** Next.js 16, React 19, TypeScript, Tailwind CSS v4, TanStack Query, Zustand, ECharts, Framer Motion

## Quick start (local)

Prerequisites: JDK 17+, Maven 3.9+, Node 20+, PostgreSQL 14+ with an empty database `payops360`.

```bash
# 1. Backend (dev profile seeds a demo tenant and starts the live traffic simulator)
cd backend
DB_PASSWORD=postgres mvn spring-boot:run          # http://localhost:8417  (Swagger: /swagger-ui.html)

# 2. Frontend
cd frontend
npm install
npm run dev                                       # http://localhost:3417
```

Sign in at <http://localhost:3417> as `admin@payops360.io` with password `PayOps#Admin2026`.

The demo tenant also has these users, all with the same dev password:
- `ops@payops360.io` (Operations Manager)
- `analyst@payops360.io` (Analyst)
- `support@payops360.io` (Support)
- `auditor@payops360.io` (Auditor)

The demo tenant has MFA optional so you can explore quickly. Enable MFA under **Security**; production tenants default to mandatory MFA.

### Enable generative AI (Claude)

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # optional; default model claude-opus-5-5 (override with PAYOPS_AI_MODEL)
```

Without a key, every AI feature still works on the built-in deterministic insight engine.

### Demo scenario

The simulator streams about 40 payments per minute. Every 30 minutes, at minutes 20 to 28, the `checkout` provider degrades. Watch the following happen live:
- AI anomaly alerts;
- automatic incident correlation;
- the health score dropping;
- reroute recommendations.

Open the incident and click **Investigate with AI**.

### Tests

```bash
cd backend && mvn test                     # domain, security and ArchUnit architecture tests
node scripts/smoke-test.mjs                # 59 end-to-end API checks against a running dev backend
pwsh scripts/reset-dev-db.ps1              # recreate the local dev database (re-seeded on next start)
```

## Configuration (environment)

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Base64 HMAC key (≥ 256 bits; required outside dev) |
| `DATA_ENCRYPTION_KEY` | Base64 AES-256 key for MFA secrets (required outside dev) |
| `ALLOWED_ORIGINS` | CORS allow-list |
| `ANTHROPIC_API_KEY`, `PAYOPS_AI_MODEL` | Optional generative AI |
| `PAYOPS_PORT` | API port (default 8417) |
| `BACKEND_URL` | Frontend → API proxy target (default `http://localhost:8417`) |
