package com.payops.analytics.adapter.out.persistence;

import com.payops.analytics.application.port.out.AnalyticsQueryPort;
import com.payops.analytics.domain.AnalyticsModels.Breakdown;
import com.payops.analytics.domain.AnalyticsModels.Corridor;
import com.payops.analytics.domain.AnalyticsModels.ProviderPerformance;
import com.payops.analytics.domain.AnalyticsModels.TimeBucket;
import com.payops.analytics.domain.AnalyticsModels.WindowStats;
import com.payops.analytics.domain.FxRates;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * SQL read model over the payments table. All queries are tenant scoped and
 * parameterised; the only interpolated fragments are compile-time constants.
 */
@Component
@RequiredArgsConstructor
class JdbcAnalyticsQueryAdapter implements AnalyticsQueryPort {

    private static final String USD = FxRates.sqlToUsd("p.amount", "p.currency");
    private static final String SUCCESS = "p.status IN ('AUTHORIZED','CAPTURED','PROCESSING','SETTLED','REFUNDED','CHARGEBACK')";
    private static final String FAILURE = "p.status IN ('FAILED','RETRY_FAILED')";
    private static final String IN_FLIGHT = "p.status IN ('INITIATED','AUTHORIZED','CAPTURED','PROCESSING','RETRY_PENDING','RETRY_IN_PROGRESS')";

    private final JdbcTemplate jdbc;

    @Override
    public WindowStats windowStats(long organizationId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE %s) AS succeeded,
                       count(*) FILTER (WHERE %s) AS failed,
                       count(*) FILTER (WHERE %s) AS in_flight,
                       count(*) FILTER (WHERE p.stuck) AS stuck,
                       count(*) FILTER (WHERE p.risk_level IN ('HIGH','CRITICAL')) AS high_risk,
                       coalesce(sum(%s) FILTER (WHERE %s), 0) AS volume,
                       coalesce(sum(%s) FILTER (WHERE %s), 0) AS failed_volume,
                       coalesce(avg(p.latency_ms), 0)::int AS avg_latency,
                       coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY p.latency_ms), 0)::int AS p95_latency,
                       count(DISTINCT p.customer_ref) AS customers
                FROM payments p
                WHERE p.organization_id = ? AND p.created_at >= ? AND p.created_at < ?
                """.formatted(SUCCESS, FAILURE, IN_FLIGHT, USD, SUCCESS, USD, FAILURE),
                (rs, i) -> new WindowStats(rs.getLong("total"), rs.getLong("succeeded"), rs.getLong("failed"),
                        rs.getLong("in_flight"), rs.getLong("stuck"), rs.getLong("high_risk"),
                        money(rs.getBigDecimal("volume")), money(rs.getBigDecimal("failed_volume")),
                        rs.getInt("avg_latency"), rs.getInt("p95_latency"), rs.getLong("customers")),
                organizationId, ts(from), ts(to));
    }

    @Override
    public List<TimeBucket> timeseries(long organizationId, Instant from, Instant to, Duration bucket) {
        return jdbc.query("""
                SELECT gs AS bucket,
                       count(p.id) AS total,
                       count(p.id) FILTER (WHERE %s) AS succeeded,
                       count(p.id) FILTER (WHERE %s) AS failed,
                       coalesce(sum(%s) FILTER (WHERE %s), 0) AS volume,
                       coalesce(avg(p.latency_ms), 0)::int AS avg_latency
                FROM generate_series(CAST(? AS timestamptz), CAST(? AS timestamptz) - interval '1 second',
                                     make_interval(secs => ?)) gs
                LEFT JOIN payments p ON p.organization_id = ? AND p.created_at >= gs
                                     AND p.created_at < gs + make_interval(secs => ?)
                GROUP BY gs ORDER BY gs
                """.formatted(SUCCESS, FAILURE, USD, SUCCESS),
                (rs, i) -> new TimeBucket(rs.getTimestamp("bucket").toInstant(), rs.getLong("total"),
                        rs.getLong("succeeded"), rs.getLong("failed"), money(rs.getBigDecimal("volume")),
                        rs.getInt("avg_latency")),
                ts(from), ts(to), bucket.toSeconds(), organizationId, bucket.toSeconds());
    }

    @Override
    public List<ProviderPerformance> providerPerformance(long organizationId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT pr.id, pr.code, pr.name, pr.sla_success_rate, pr.sla_latency_ms, pr.cost_bps,
                       count(p.id) AS total,
                       count(p.id) FILTER (WHERE %s) AS succeeded,
                       count(p.id) FILTER (WHERE %s) AS failed,
                       coalesce(avg(p.latency_ms), 0)::int AS avg_latency,
                       coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY p.latency_ms), 0)::int AS p95_latency,
                       coalesce(sum(%s) FILTER (WHERE %s), 0) AS volume
                FROM providers pr
                LEFT JOIN payments p ON p.provider_id = pr.id AND p.created_at >= ? AND p.created_at < ?
                WHERE pr.organization_id = ?
                GROUP BY pr.id ORDER BY volume DESC
                """.formatted(SUCCESS, FAILURE, USD, SUCCESS),
                (rs, i) -> {
                    long succeeded = rs.getLong("succeeded");
                    long failed = rs.getLong("failed");
                    double rate = succeeded + failed == 0 ? 0 : succeeded * 100.0 / (succeeded + failed);
                    return new ProviderPerformance(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                            rs.getLong("total"), succeeded, failed, rate, rs.getInt("avg_latency"),
                            rs.getInt("p95_latency"), money(rs.getBigDecimal("volume")),
                            rs.getBigDecimal("sla_success_rate"), rs.getInt("sla_latency_ms"), rs.getBigDecimal("cost_bps"));
                },
                ts(from), ts(to), organizationId);
    }

    @Override
    public List<Breakdown> failureReasons(long organizationId, Instant from, Instant to) {
        return breakdown("coalesce(p.failure_code, 'UNSPECIFIED')", FAILURE, organizationId, from, to, 12);
    }

    @Override
    public List<Breakdown> methodMix(long organizationId, Instant from, Instant to) {
        return breakdown("p.method", "TRUE", organizationId, from, to, 10);
    }

    @Override
    public List<Breakdown> riskDistribution(long organizationId, Instant from, Instant to) {
        return breakdown("p.risk_level", "TRUE", organizationId, from, to, 4);
    }

    @Override
    public List<Corridor> corridors(long organizationId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT coalesce(p.country, '??') AS country, count(*) AS total,
                       coalesce(sum(%s), 0) AS volume,
                       count(*) FILTER (WHERE %s) AS succeeded,
                       count(*) FILTER (WHERE %s) AS failed
                FROM payments p
                WHERE p.organization_id = ? AND p.created_at >= ? AND p.created_at < ?
                GROUP BY 1 ORDER BY volume DESC LIMIT 15
                """.formatted(USD, SUCCESS, FAILURE),
                (rs, i) -> {
                    long s = rs.getLong("succeeded");
                    long f = rs.getLong("failed");
                    return new Corridor(rs.getString("country"), rs.getLong("total"), money(rs.getBigDecimal("volume")),
                            s + f == 0 ? 0 : s * 100.0 / (s + f));
                },
                organizationId, ts(from), ts(to));
    }

    private List<Breakdown> breakdown(String labelExpression, String filter, long organizationId, Instant from,
                                      Instant to, int limit) {
        return jdbc.query("""
                SELECT %s AS label, count(*) AS total, coalesce(sum(%s), 0) AS volume
                FROM payments p
                WHERE p.organization_id = ? AND p.created_at >= ? AND p.created_at < ? AND %s
                GROUP BY 1 ORDER BY total DESC LIMIT %d
                """.formatted(labelExpression, USD, filter, limit),
                (rs, i) -> new Breakdown(rs.getString("label"), rs.getLong("total"), money(rs.getBigDecimal("volume"))),
                organizationId, ts(from), ts(to));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
