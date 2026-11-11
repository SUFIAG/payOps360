package com.payops.provider.adapter.out.persistence;

import com.payops.provider.application.port.out.HealthSnapshotRepository;
import com.payops.provider.application.port.out.TrafficStatisticsPort;
import com.payops.provider.domain.HealthSnapshot;
import com.payops.provider.domain.HealthStatus;
import com.payops.provider.domain.TrafficWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class JdbcHealthSnapshotRepository implements HealthSnapshotRepository, TrafficStatisticsPort {

    private static final String SUCCESS_STATES = "('AUTHORIZED','CAPTURED','PROCESSING','SETTLED','REFUNDED','CHARGEBACK')";
    private static final String FAILURE_STATES = "('FAILED','RETRY_FAILED')";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    @Override
    public void saveAll(List<HealthSnapshot> snapshots) {
        jdbc.batchUpdate("""
                INSERT INTO provider_health_snapshots (provider_id, window_start, window_end, total_count, success_count,
                    failure_count, success_rate, avg_latency_ms, p95_latency_ms, health_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshots.stream().map(s -> new Object[]{
                s.providerId(), Timestamp.from(s.windowStart()), Timestamp.from(s.windowEnd()), s.total(),
                s.succeeded(), s.failed(), s.successRate(), s.avgLatencyMs(), s.p95LatencyMs(), s.status().name()
        }).toList());
    }

    @Override
    public Map<Long, HealthSnapshot> latestFor(List<Long> providerIds) {
        if (providerIds.isEmpty()) {
            return Map.of();
        }
        return named.query("""
                SELECT DISTINCT ON (provider_id) * FROM provider_health_snapshots
                WHERE provider_id IN (:ids) ORDER BY provider_id, window_end DESC
                """, new MapSqlParameterSource("ids", providerIds), snapshotMapper()).stream()
                .collect(Collectors.toMap(HealthSnapshot::providerId, Function.identity()));
    }

    @Override
    public List<HealthSnapshot> history(long providerId, Instant since) {
        return jdbc.query("SELECT * FROM provider_health_snapshots WHERE provider_id = ? AND window_end >= ? ORDER BY window_end",
                snapshotMapper(), providerId, Timestamp.from(since));
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM provider_health_snapshots WHERE window_end < ?", Timestamp.from(cutoff));
    }

    @Override
    public List<TrafficWindow> trafficByProvider(Instant from, Instant to) {
        return jdbc.query("""
                SELECT provider_id,
                       count(*) AS total,
                       count(*) FILTER (WHERE status IN %s) AS succeeded,
                       count(*) FILTER (WHERE status IN %s) AS failed,
                       coalesce(avg(latency_ms), 0)::int AS avg_latency,
                       coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms), 0)::int AS p95_latency
                FROM payments
                WHERE created_at >= ? AND created_at < ?
                GROUP BY provider_id
                """.formatted(SUCCESS_STATES, FAILURE_STATES),
                (rs, i) -> new TrafficWindow(rs.getLong("provider_id"), from, to, rs.getInt("total"),
                        rs.getInt("succeeded"), rs.getInt("failed"), rs.getInt("avg_latency"), rs.getInt("p95_latency")),
                Timestamp.from(from), Timestamp.from(to));
    }

    private static RowMapper<HealthSnapshot> snapshotMapper() {
        return (rs, i) -> new HealthSnapshot(rs.getLong("provider_id"), rs.getTimestamp("window_start").toInstant(),
                rs.getTimestamp("window_end").toInstant(), rs.getInt("total_count"), rs.getInt("success_count"),
                rs.getInt("failure_count"), rs.getBigDecimal("success_rate"), rs.getInt("avg_latency_ms"),
                rs.getInt("p95_latency_ms"), HealthStatus.valueOf(rs.getString("health_status")));
    }
}
