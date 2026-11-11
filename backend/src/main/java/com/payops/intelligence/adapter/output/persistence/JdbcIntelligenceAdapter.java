package com.payops.intelligence.adapter.output.persistence;

import com.payops.analytics.domain.FxRates;
import com.payops.intelligence.application.port.output.AiGovernancePort;
import com.payops.intelligence.application.port.output.CustomerBehaviorPort;
import com.payops.intelligence.application.port.output.ProviderSeriesPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class JdbcIntelligenceAdapter implements CustomerBehaviorPort, ProviderSeriesPort, AiGovernancePort {

    private static final String USD = FxRates.sqlToUsd("amount", "currency");

    private final JdbcTemplate jdbc;

    @Override
    public CustomerProfile profile(long organizationId, String customerRef, Instant at) {
        return jdbc.queryForObject("""
                SELECT coalesce(avg(%s) FILTER (WHERE created_at >= ?), 0) AS avg_usd,
                       count(*) FILTER (WHERE created_at >= ?) AS payments_30d,
                       count(*) FILTER (WHERE created_at >= ?) AS payments_1h,
                       count(*) FILTER (WHERE created_at >= ? AND status IN ('FAILED','RETRY_FAILED')) AS failures_24h,
                       count(DISTINCT country) FILTER (WHERE created_at >= ?) AS countries_24h
                FROM payments
                WHERE organization_id = ? AND customer_ref = ? AND created_at >= ? AND created_at < ?
                """.formatted(USD),
                (rs, i) -> new CustomerProfile(rs.getBigDecimal("avg_usd").setScale(2, RoundingMode.HALF_UP),
                        rs.getLong("payments_30d"), rs.getLong("payments_1h"), rs.getLong("failures_24h"),
                        rs.getLong("countries_24h")),
                ts(at.minus(Duration.ofDays(30))), ts(at.minus(Duration.ofDays(30))), ts(at.minus(Duration.ofHours(1))),
                ts(at.minus(Duration.ofHours(24))), ts(at.minus(Duration.ofHours(24))),
                organizationId, customerRef, ts(at.minus(Duration.ofDays(30))), ts(at));
    }

    @Override
    public List<ProviderSeries> series(Instant from, Instant to, Duration bucket) {
        Map<Long, ProviderSeries> byProvider = new LinkedHashMap<>();
        jdbc.query("""
                SELECT pr.organization_id, pr.id AS provider_id, pr.code, pr.name, gs AS bucket,
                       count(p.id) AS total,
                       count(p.id) FILTER (WHERE p.status IN ('FAILED','RETRY_FAILED')) AS failed,
                       coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY p.latency_ms), 0)::int AS p95
                FROM providers pr
                CROSS JOIN generate_series(CAST(? AS timestamptz), CAST(? AS timestamptz) - interval '1 second',
                                           make_interval(secs => ?)) gs
                LEFT JOIN payments p ON p.provider_id = pr.id AND p.created_at >= gs
                                     AND p.created_at < gs + make_interval(secs => ?)
                WHERE pr.active
                GROUP BY pr.organization_id, pr.id, pr.code, pr.name, gs
                ORDER BY pr.id, gs
                """, rs -> {
            long providerId = rs.getLong("provider_id");
            ProviderSeries series = byProvider.computeIfAbsent(providerId, id -> {
                try {
                    return new ProviderSeries(rs.getLong("organization_id"), id, rs.getString("code"),
                            rs.getString("name"), new ArrayList<>());
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            series.buckets().add(new Bucket(rs.getTimestamp("bucket").toInstant(), rs.getLong("total"),
                    rs.getLong("failed"), rs.getInt("p95")));
        }, ts(from), ts(to), bucket.toSeconds(), bucket.toSeconds());
        return new ArrayList<>(byProvider.values());
    }

    @Override
    public boolean generativeAiEnabled(long organizationId) {
        Boolean enabled = jdbc.queryForObject("SELECT ai_enabled FROM organizations WHERE id = ?", Boolean.class,
                organizationId);
        return Boolean.TRUE.equals(enabled);
    }

    @Override
    public void recordUsage(long organizationId, Long userId, String feature, String engine, long inputTokens,
                            long outputTokens, long latencyMs, boolean success) {
        jdbc.update("""
                INSERT INTO ai_interactions (organization_id, user_id, feature, engine, input_tokens, output_tokens,
                                             latency_ms, success)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, organizationId, userId, feature, engine, (int) inputTokens, (int) outputTokens,
                (int) Math.min(latencyMs, Integer.MAX_VALUE), success);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
