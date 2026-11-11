# PayOps360 — Architecture

## System context

```
 Operators (browser) ──HTTPS──► Next.js 16 web tier ──/api/v1 proxy──► Spring Boot 3.5 API ──► PostgreSQL
                                  (CSP, same-origin cookies)             │  ├─ SSE live stream
 Provider webhooks / merchant systems ──────────────────────────────────►│  ├─ Scheduled jobs (ShedLock)
                                                                          │  └─ Claude API (optional, Gen-AI)
```

## Backend: hexagonal modular monolith

Each business module follows **ports and adapters**:

```
com.payops.<module>
├── domain/                 pure Java: aggregates, value objects, domain services (no Spring/JPA/Jackson)
├── application/
│   ├── port/in/            use-case interfaces + view models (driving ports)
│   ├── port/out/           repository / gateway interfaces (driven ports)
│   └── service/            use-case implementations (transactions, orchestration)
└── adapter/
    ├── in/web | scheduler  REST controllers, scheduled jobs (driving adapters)
    └── out/persistence|... JPA/JDBC, Claude, cross-module bridges (driven adapters)
```

| Module | Responsibility |
|---|---|
| `identity` | Users, organisations, authentication, MFA, sessions, RBAC, Spring Security wiring |
| `audit` | Tamper-evident audit trail, integrity verification |
| `payment` | Payment aggregate and state machine, ingestion (idempotent), search, stuck detection |
| `provider` | Provider catalogue, SLA, health snapshots |
| `alert` | Alert lifecycle, de-duplication, auto-resolve |
| `incident` | Incident lifecycle, alert correlation, impact, AI analysis attachment |
| `analytics` | CQRS read model: KPIs, time series, breakdowns (JDBC, SQL aggregates) |
| `intelligence` | AI: risk engine, anomaly detection, forecasting, routing, copilot, RCA, smart search, briefing |
| `shared` | Kernel: domain primitives, API envelope, error handling, correlation ids, SSE fan-out, platform config |
| `bootstrap` | Dev-only demo tenant seeder and traffic simulator |

**Rules enforced by ArchUnit (`HexagonalArchitectureTest`):**
- The domain is framework-free.
- `application` never depends on adapters or on web or persistence frameworks.
- Modules talk to each other only through ports, never through another module's adapters or services.
- The shared kernel does not depend on business modules.
- Domain packages are acyclic.

### Cross-module collaboration (all through ports)

```
payment ──RiskScoringPort──► intelligence (RiskScoringAdapter → RiskEngine)
payment ──ProviderDirectory──► provider (ProviderCatalogUseCase)
payment ──PaymentSignalPort──► alert (RaiseAlertUseCase)           [high-risk, stuck]
alert ──AlertContextPort──► incident (IncidentCorrelationUseCase)  [auto-correlation]
intelligence ──AlertingPort──► alert                               [AI anomalies]
intelligence ──OperationsDataPort──► analytics/provider/incident/alert/payment use cases
* ──AuditTrail──► audit
```

### Scheduled jobs (cluster-safe)

| Job | Interval | Purpose |
|---|---|---|
| `ProviderHealthJob` | 1 min | 15-minute rolling health per provider vs SLA |
| `AnomalyScanJob` | 1 min | Robust anomaly detection, raises AI alerts |
| `StuckPaymentJob` | 1 min | Flags payments exceeding per-state time budgets |
| `AlertAutoResolveJob` | 2 min | Resolves alerts quiet for 15 min |

### Data model

A single Flyway baseline (`V1__baseline_schema.sql`):
- identity primary keys, UUID public ids, `organization_id` on every business table;
- `TIMESTAMPTZ` timestamps, `NUMERIC(19,4)` money, JSONB for risk factors and AI recommendations;
- partial indexes for hot paths.

## Frontend: Next.js 16 App Router

```
app/(auth)/login              multi-step sign-in: credentials → MFA / enrolment → recovery codes
app/(console)/*               authenticated console (AppShell: nav, command palette, copilot drawer, live stream)
components/ui                 design-system primitives (Small World-inspired lime on navy)
components/{dashboard,ai,...} feature components
lib/api                       typed endpoint client, single-flight token refresh
lib/auth                      in-memory session store
lib/live                      authenticated SSE client with back-off
proxy.ts                      optimistic auth redirect (Next 16 proxy)
```

- TanStack Query handles server state, Zustand handles UI and session state, ECharts draws charts, Framer Motion animates.
- All API calls are same-origin (`/api/v1/*` rewrite), so the refresh cookie stays first-party and `SameSite=Strict`.

## Running locally

See the root [README](../README.md).
