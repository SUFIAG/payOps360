# PayOps360 — Business Requirements Document

| | |
|---|---|
| Product | PayOps360 — AI-native payment operations command center |
| Version | 1.0 (MVP scope baseline) |
| Status | Approved for build |

## 1. Problem statement

Payment businesses route money through many external providers: card acquirers, gateways, real-time networks, bank rails, wallets and cash-pickup partners. When one of them degrades, payments fail silently. Operations teams usually notice late, through customer complaints or end-of-day reports. They then spend hours across provider portals, logs and spreadsheets to find the cause.

The industry cost is large:
- Failed and false-declined payments lose revenue.
- Customer churn follows bad payment experiences.
- Provider SLA breaches go unclaimed.
- Incident response is slow (high mean time to resolve).

## 2. Vision

> One pane of glass for every payment in flight. An AI co-pilot sees problems first, explains them in plain language and recommends the next best action.

PayOps360 replaces reactive, manual payment monitoring with an **AI-native command center**. It detects, explains and helps resolve payment problems in minutes, not hours.

## 3. Target audience

### 3.1 Customer segments (who buys)

| Segment | Examples | Why they need PayOps360 |
|---|---|---|
| **Cross-border remittance and money-transfer operators** (primary) | Small World-style MTOs, corridor specialists, diaspora remittance apps | Dozens of payout partners per corridor; each failed transfer is a lost customer and a compliance risk. They need corridor-level visibility and smart partner routing. |
| **Payment service providers and payment facilitators** | Regional PSPs, PayFacs, orchestration platforms | Multi-acquirer routing, authorisation-rate optimisation, SLA management with acquirers. |
| **Digital banks and e-money institutions (EMIs)** | Neobanks, wallet providers | Real-time rails (RTP, instant SEPA, local schemes) need 24/7 monitoring and fast incident response. |
| **High-volume merchants and marketplaces** | E-commerce, travel, gig platforms | Checkout conversion depends directly on payment success; they need failure insight and provider failover. |
| **Banks' payment operations and treasury teams** | Cash management, SWIFT/gpi operations | Exception handling for cross-border wires and stuck payments. |

### 3.2 User personas (who uses it daily)

| Persona | Role in product | Goals |
|---|---|---|
| **Payment Operations Analyst** | `ANALYST` / `SUPPORT` | Watch live traffic, investigate failed or stuck payments, answer "what happened to payment X?" |
| **Operations Manager / Incident Commander** | `OPERATIONS_MANAGER` | Respond to provider outages, run incidents, reroute traffic, report MTTR |
| **Head of Payments / COO** | `ANALYST` (read) | Daily performance, provider SLA, revenue at risk, cost optimisation |
| **Risk and Fraud Analyst** | `ANALYST` | Review high-risk payments and explainable risk factors |
| **Compliance Officer / Internal Auditor** | `AUDITOR` | Tamper-evident audit trail, access reviews, evidence for PCI DSS and SOC 2 |
| **Platform Administrator** | `ADMIN` | User provisioning, roles, MFA policy, provider catalogue |

### 3.3 Differentiation (why it stands out)

1. **AI-native, not AI-bolted-on.** Every screen is backed by intelligence:
   - real-time anomaly detection;
   - explainable risk scoring;
   - automated root-cause analysis;
   - smart routing;
   - forecasting;
   - a conversational copilot grounded in live data.
2. **Explainable and governed AI.** Every score shows its factors. Every LLM call is policy-gated per tenant, PII-masked and usage-logged. Deterministic engines keep working when the LLM is unavailable.
3. **Remittance-grade.** Corridor analytics, payout-partner health and cash-pickup and wallet rails come out of the box, not just cards.
4. **Security first.** MFA, refresh-token rotation with theft detection, progressive lockout, a hash-chained audit trail and least-privilege RBAC are built in from day one.
5. **Time to value.** Live demo data and a simulator show the full detect → correlate → explain → resolve loop in minutes.

## 4. Scope

### 4.1 In scope (v1.0)

| # | Capability | Description |
|---|---|---|
| F1 | Payment ingestion | REST ingestion with `Idempotency-Key`, ISO-4217 validation, state machine (12 states), lifecycle timeline |
| F2 | Payment explorer | Search and filter: status, provider, method, risk, amount, currency, corridor, stuck, time. **AI natural-language search.** |
| F3 | Provider catalogue and health | SLA definitions (success rate, p95 latency, cost), rolling 15-minute health snapshots, health history |
| F4 | Real-time command center | Live KPIs vs previous period, live transaction stream (SSE), provider matrix, AI health score |
| F5 | Alerting | Rule-based and AI alerts with fingerprint de-duplication, escalation, acknowledge/resolve, auto-resolve when quiet |
| F6 | Incident management | Automatic alert-to-incident correlation, impact (payments and amount), lifecycle, timeline, MTTA/MTTR |
| F7 | Analytics | Volume, success rate, latency, provider comparison, failure reasons, method mix, risk distribution, corridors |
| F8 | AI capabilities | See section 5 |
| F9 | Identity and access | Admin-provisioned users, 5 least-privilege roles, MFA (TOTP + recovery codes), sessions, password policy |
| F10 | Audit and governance | Tamper-evident audit trail with integrity verification; AI usage ledger |
| F11 | Multi-tenancy | Every business record scoped to an organisation; tenant-isolated live stream |

### 4.2 Out of scope (v1.0), roadmap candidates

- Executing payments or holding funds. PayOps360 is an operations layer, not a processor.
- Automatic traffic rerouting. v1 recommends; a human executes in the orchestration layer. Closed-loop routing is planned for v1.2.
- SSO (SAML/OIDC) and SCIM provisioning. Planned for v1.1.
- Email, SMS, Slack and PagerDuty notifications. Planned for v1.1.
- Self-service password reset by email. In v1 an admin resets the password.
- Reconciliation and settlement matching, chargeback workflow.
- Mobile apps.

## 5. AI and generative AI capabilities

| Capability | Type | Automation delivered |
|---|---|---|
| **Ops Health Score** | Composite model | A single 0–100 score with a plain-language headline, refreshed every 20 s |
| **Anomaly detection** | Robust statistics (modified z-score, median/MAD) per provider and metric | Detects failure-rate spikes, latency spikes, volume drops and surges, and outages without fixed thresholds; raises AI alerts automatically every minute |
| **Incident auto-correlation** | Rules | Groups related alerts by provider and time window into one incident with live impact |
| **AI root-cause analysis** | LLM (Claude) + deterministic evidence engine | Collects evidence and produces summary, root cause, ranked actions and a confidence score |
| **Explainable risk scoring** | Behavioural model | Scores every payment at ingestion (velocity, amount anomaly, card-testing pattern, geo spread, corridor monitoring, reporting threshold) with human-readable factors; CRITICAL raises an alert |
| **Failure intelligence** | Knowledge base | Classifies decline codes (soft/hard/technical/risk/compliance), states retryability, operator action and customer-safe message |
| **Smart routing advisor** | Multi-criteria ranking | Ranks providers by live success rate, latency and cost, penalising degraded ones |
| **Volume forecasting** | Holt smoothing + daily seasonality | 12-hour forecast with 80% band, used for capacity warnings |
| **Prescriptive recommendations** | Rules over AI signals | Next best actions: reroute, enable retries, reconcile stuck payments, review risk, cost savings |
| **Ops Copilot** | LLM with read-only tool use | Conversational analyst over live data (11 tools); falls back to an intent engine without an LLM |
| **Natural-language search** | LLM structured output + parser | "failed card payments over 500 GBP to Pakistan today" becomes filters |
| **Payment explainer** | LLM + knowledge base | Plain-language "what happened and what to do" for any payment |
| **Executive briefing** | LLM narrative | Daily summary for leadership |

## 6. Non-functional requirements

| Area | Requirement |
|---|---|
| Security | OWASP ASVS L2 controls. Aligned with PCI DSS v4.0 req. 7, 8, 10 (no PAN stored: only last 4 digits). See [SECURITY.md](SECURITY.md). |
| Availability | Stateless API, horizontally scalable. Scheduled jobs use cluster locks (ShedLock). |
| Performance | p95 API latency < 300 ms for list and detail endpoints at 1M payments; dashboard refresh ≤ 20 s; live events < 1 s |
| Data | PostgreSQL. Flyway-versioned schema. UTC timestamps. NUMERIC money with ISO-4217 scale. |
| Observability | Correlation IDs on every request and log line, Prometheus metrics, health/readiness probes |
| Auditability | Security-relevant actions recorded in a hash-chained trail; integrity verifiable on demand |
| AI governance | Tenant-level AI toggle, PII masking, prompt-injection guardrails, read-only tools, usage ledger, deterministic fallback |
| Accessibility | WCAG 2.1 AA colour contrast in both themes; keyboard navigation (Ctrl K, Ctrl J) |

## 7. Success metrics (KPIs for customers)

- Mean time to detect provider degradation under 2 minutes.
- MTTR reduced by at least 40% versus baseline.
- Authorisation/payout success uplift of 1–3 pp through routing advice.
- Revenue at risk visible in real time.
- 100% of privileged actions audit-logged.
