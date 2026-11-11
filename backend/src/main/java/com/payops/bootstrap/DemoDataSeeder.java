package com.payops.bootstrap;

import com.payops.analytics.domain.FxRates;
import com.payops.identity.application.port.out.OrganizationRepository;
import com.payops.identity.application.port.out.PasswordHasher;
import com.payops.identity.application.port.out.UserRepository;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.User;
import com.payops.intelligence.domain.risk.RiskEngine;
import com.payops.intelligence.domain.risk.RiskScore;
import com.payops.intelligence.domain.risk.RiskSignals;
import com.payops.payment.domain.PaymentStatus;
import com.payops.shared.domain.References;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a demo tenant with users, providers and 72 hours of realistic payment
 * history on an empty database. Development profile only.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "payops.bootstrap", name = "enabled", havingValue = "true")
@Order(1)
@RequiredArgsConstructor
class DemoDataSeeder implements ApplicationRunner {

    private static final int HISTORY_HOURS = 72;
    private static final int BASE_PER_HOUR = 110;

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${payops.bootstrap.admin-email}")
    private String adminEmail;
    @Value("${payops.bootstrap.admin-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (organizations.count() > 0) {
            return;
        }
        log.info("Empty database detected: seeding demo tenant");
        Organization org = organizations.save(new Organization(null, UUID.randomUUID(), "Atlas Remit (Demo)",
                "atlas-remit", false, true, "USD"));
        seedUsers(org.id());
        seedProviders(org.id());
        int payments = seedHistory(org.id());
        seedResolvedIncident(org.id());
        log.info("Demo tenant ready: {} historical payments. Sign in as {}", payments, adminEmail);
    }

    private void seedUsers(long orgId) {
        Instant now = clock.instant();
        String hash = passwordHasher.hash(adminPassword);
        users.save(User.register(orgId, adminEmail, "Amira Khan", hash, Role.ADMIN, false, now));
        users.save(User.register(orgId, "ops@payops360.io", "Daniel Okafor", hash, Role.OPERATIONS_MANAGER, false, now));
        users.save(User.register(orgId, "analyst@payops360.io", "Priya Raman", hash, Role.ANALYST, false, now));
        users.save(User.register(orgId, "support@payops360.io", "Luis Ortega", hash, Role.SUPPORT, false, now));
        users.save(User.register(orgId, "auditor@payops360.io", "Grace Chen", hash, Role.AUDITOR, false, now));
    }

    private void seedProviders(long orgId) {
        jdbc.batchUpdate("""
                INSERT INTO providers (organization_id, code, name, type, region, sla_latency_ms, sla_success_rate, cost_bps)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, DemoCatalog.PROVIDERS.stream().map(p -> new Object[]{orgId, p.code(), p.name(), p.type().name(),
                p.region(), p.slaLatencyMs(), p.slaSuccessRate(), p.costBps()}).toList());
    }

    private int seedHistory(long orgId) {
        DemoTrafficGenerator generator = new DemoTrafficGenerator(42);
        Instant end = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Instant start = end.minus(Duration.ofHours(HISTORY_HOURS));
        List<Object[]> paymentRows = new ArrayList<>();
        List<Object[]> eventRows = new ArrayList<>();

        for (Instant hour = start; hour.isBefore(end); hour = hour.plus(Duration.ofHours(1))) {
            int count = (int) Math.round(BASE_PER_HOUR * DemoTrafficGenerator.intensity(hour)
                    * (0.85 + generator.random().nextDouble() * 0.3));
            // A 40-minute outage on the unstable provider yesterday gives the analytics a story to tell.
            boolean pastOutage = Duration.between(hour, end).toHours() == 26;
            for (int i = 0; i < count; i++) {
                Instant at = hour.plusSeconds(generator.random().nextInt(3600));
                if (!at.isBefore(end)) {
                    continue;
                }
                double degradation = pastOutage && at.atZone(ZoneOffset.UTC).getMinute() < 40 ? 0.9 : 0;
                DemoTrafficGenerator.GeneratedPayment g = settleIfOld(generator.next(degradation), at, end);
                String reference = References.next("PAY");
                RiskScore risk = RiskEngine.score(new RiskSignals(FxRates.toUsd(g.amount(), g.currency()),
                        BigDecimal.valueOf(350), generator.random().nextInt(25),
                        generator.random().nextDouble() < 0.03 ? 5 : generator.random().nextInt(2),
                        g.outcome() == PaymentStatus.FAILED && generator.random().nextDouble() < 0.1 ? 3 : 0,
                        generator.random().nextDouble() < 0.01 ? 3 : 1, g.method().name(), g.country(),
                        at.atZone(ZoneOffset.UTC).getHour()));
                Instant finalAt = at.plusMillis(g.latencyMs());
                paymentRows.add(new Object[]{orgId, reference, g.amount(), g.currency(), g.provider().code(), orgId,
                        g.customerRef(), g.method().name(), g.cardLast4(), g.country(), g.outcome().name(),
                        Timestamp.from(finalAt), g.failureCode(), g.failureCode() == null ? null : failureMessage(g.failureCode()),
                        g.latencyMs(), risk.score(), risk.level(), toJson(risk.factors()), Timestamp.from(at), Timestamp.from(finalAt)});
                for (Object[] e : events(reference, g, at)) {
                    eventRows.add(e);
                }
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO payments (organization_id, reference, amount, currency, provider_id, customer_ref, method,
                    card_last4, country, status, status_changed_at, failure_code, failure_message, latency_ms,
                    risk_score, risk_level, risk_factors, created_at, updated_at)
                VALUES (?, ?, ?, ?, (SELECT id FROM providers WHERE code = ? AND organization_id = ?), ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """, paymentRows);
        jdbc.batchUpdate("""
                INSERT INTO payment_events (payment_id, from_status, to_status, reason, error_code, actor, occurred_at)
                VALUES ((SELECT id FROM payments WHERE reference = ?), ?, ?, ?, ?, 'provider-webhook', ?)
                """, eventRows);
        return paymentRows.size();
    }

    /** History older than a few minutes has completed its lifecycle; only recent payments stay in flight. */
    private static DemoTrafficGenerator.GeneratedPayment settleIfOld(DemoTrafficGenerator.GeneratedPayment g, Instant at,
                                                                     Instant end) {
        boolean inFlight = g.outcome() == PaymentStatus.CAPTURED || g.outcome() == PaymentStatus.PROCESSING;
        if (!inFlight || Duration.between(at, end).toMinutes() < 4) {
            return g;
        }
        return new DemoTrafficGenerator.GeneratedPayment(g.provider(), g.customerRef(), g.amount(), g.currency(),
                g.country(), g.method(), g.cardLast4(), PaymentStatus.SETTLED, null, g.latencyMs());
    }

    private static List<Object[]> events(String reference, DemoTrafficGenerator.GeneratedPayment g, Instant at) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{reference, null, "INITIATED", "Payment initiated", null, Timestamp.from(at)});
        long step = Math.max(1, g.latencyMs() / 3);
        if (g.outcome() == PaymentStatus.FAILED) {
            rows.add(new Object[]{reference, "INITIATED", "FAILED", "Reported by provider", g.failureCode(),
                    Timestamp.from(at.plusMillis(g.latencyMs()))});
            return rows;
        }
        rows.add(new Object[]{reference, "INITIATED", "AUTHORIZED", "Reported by provider", null, Timestamp.from(at.plusMillis(step))});
        if (g.outcome() == PaymentStatus.AUTHORIZED) {
            return rows;
        }
        rows.add(new Object[]{reference, "AUTHORIZED", "CAPTURED", "Reported by provider", null, Timestamp.from(at.plusMillis(step * 2))});
        if (g.outcome() == PaymentStatus.CAPTURED) {
            return rows;
        }
        String next = g.outcome() == PaymentStatus.PROCESSING ? "PROCESSING" : "SETTLED";
        rows.add(new Object[]{reference, "CAPTURED", next, "Reported by provider", null, Timestamp.from(at.plusMillis(g.latencyMs()))});
        return rows;
    }

    private void seedResolvedIncident(long orgId) {
        Instant opened = clock.instant().minus(Duration.ofHours(26));
        Long providerId = jdbc.queryForObject("SELECT id FROM providers WHERE organization_id = ? AND code = ?", Long.class,
                orgId, DemoCatalog.UNSTABLE_PROVIDER);
        String reference = References.next("INC");
        jdbc.update("""
                INSERT INTO incidents (organization_id, reference, title, description, severity, status, provider_id,
                    affected_payments, affected_amount, opened_at, acknowledged_at, acknowledged_by, resolved_at, resolved_by,
                    resolution_note, ai_summary, ai_root_cause, ai_recommendations, ai_confidence, ai_engine, ai_generated_at,
                    ack_due_at, resolve_due_at)
                VALUES (?, ?, ?, ?, 'HIGH', 'RESOLVED', ?, 41, 18250.00, ?, ?, 'ops@payops360.io', ?, 'ops@payops360.io',
                    'Provider confirmed a degraded authorisation cluster; traffic failed over and recovered.',
                    'HIGH incident affecting 41 payments worth about 18250. Provider-side timeouts at Checkout.com.',
                    'Provider-side technical degradation at Checkout.com: PROCESSOR_TIMEOUT dominated failures (78%) with a p95 latency spike.',
                    CAST(? AS jsonb), 0.820, 'payops-insight-engine', ?, ?, ?)
                """, orgId, reference, "checkout: Failure-rate spike on Checkout.com",
                "Failure rate on Checkout.com jumped well above its baseline.", providerId, Timestamp.from(opened),
                Timestamp.from(opened.plus(Duration.ofMinutes(4))), Timestamp.from(opened.plus(Duration.ofMinutes(38))),
                "[\"Fail over new traffic to Adyen\", \"Open a P1 ticket with Checkout.com\", \"Enable idempotent retries after recovery\"]",
                Timestamp.from(opened.plus(Duration.ofMinutes(6))),
                // Default HIGH targets (15 min / 4 h): both met.
                Timestamp.from(opened.plus(Duration.ofMinutes(15))), Timestamp.from(opened.plus(Duration.ofHours(4))));
        Long incidentId = jdbc.queryForObject("SELECT id FROM incidents WHERE reference = ?", Long.class, reference);
        jdbc.batchUpdate("INSERT INTO incident_timeline (incident_id, kind, message, actor, occurred_at) VALUES (?, ?, ?, ?, ?)",
                List.of(
                        new Object[]{incidentId, "OPENED", "Incident opened by AI anomaly detection", "system", Timestamp.from(opened)},
                        new Object[]{incidentId, "ACKNOWLEDGED", "Acknowledged by ops@payops360.io", "ops@payops360.io", Timestamp.from(opened.plus(Duration.ofMinutes(4)))},
                        new Object[]{incidentId, "AI_ANALYSIS", "AI analysis generated (payops-insight-engine)", "ai-copilot", Timestamp.from(opened.plus(Duration.ofMinutes(6)))},
                        new Object[]{incidentId, "NOTE", "Routing weight for checkout reduced to 0%", "ops@payops360.io", Timestamp.from(opened.plus(Duration.ofMinutes(9)))},
                        new Object[]{incidentId, "RESOLVED", "Resolved: provider recovered, routing restored", "ops@payops360.io", Timestamp.from(opened.plus(Duration.ofMinutes(38)))}));
    }

    private static String failureMessage(String code) {
        return code.charAt(0) + code.substring(1).toLowerCase().replace('_', ' ');
    }

    private static String toJson(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }
}
