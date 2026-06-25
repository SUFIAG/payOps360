package com.payOps.payops360.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anomaly Detection Scheduler
 * Detects and persists anomalies in payment processing every 5 minutes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionScheduler {

    private final JdbcTemplate jdbcTemplate;

    private static final double FAILURE_RATE_THRESHOLD = 0.15; // 15%
    private static final double SUCCESS_RATE_DROP_THRESHOLD = 0.20; // 20% drop
    private static final int MIN_SAMPLE_SIZE = 10;

    /**
     * Detect anomalies every 5 minutes
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000) // 5 minutes
    public void detectAndPersistAnomalies() {
        try {
            log.info("🔍 Running anomaly detection...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'anomaly_events')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ anomaly_events table does not exist yet. Skipping anomaly detection.");
                return;
            }

            Instant now = Instant.now();
            int anomaliesDetected = 0;

            // 1. Detect high failure rate by provider
            anomaliesDetected += detectHighFailureRate(now);

            // 2. Detect success rate drops
            anomaliesDetected += detectSuccessRateDrop(now);

            // 3. Detect unusual latency spikes
            anomaliesDetected += detectLatencySpikes(now);

            // 4. Detect payment volume anomalies
            anomaliesDetected += detectVolumeAnomalies(now);

            log.info("✅ Anomaly detection complete. Found {} anomalies", anomaliesDetected);

        } catch (Exception e) {
            log.error("❌ Failed to detect anomalies", e);
        }
    }

    private int detectHighFailureRate(Instant detectedAt) {
        try {
            String sql = """
                    INSERT INTO anomaly_events 
                    (id, entity_type, entity_id, anomaly_type, severity, description, detected_at, metadata)
                    SELECT 
                        gen_random_uuid(),
                        'PROVIDER',
                        p.id::text,
                        'HIGH_FAILURE_RATE',
                        CASE 
                            WHEN failure_rate > 0.30 THEN 'CRITICAL'
                            WHEN failure_rate > 0.20 THEN 'HIGH'
                            ELSE 'MEDIUM'
                        END,
                        'Provider ' || p.name || ' has abnormally high failure rate: ' || 
                        ROUND((failure_rate * 100)::numeric, 2) || '%',
                        ?,
                        jsonb_build_object(
                            'provider_name', p.name,
                            'failure_rate', failure_rate,
                            'total_payments', total_payments,
                            'failed_payments', failed_payments,
                            'threshold', ?
                        )
                    FROM (
                        SELECT 
                            p.id,
                            p.name,
                            COUNT(pm.id) as total_payments,
                            COUNT(CASE WHEN pm.status = 'FAILED' THEN 1 END) as failed_payments,
                            CAST(COUNT(CASE WHEN pm.status = 'FAILED' THEN 1 END) AS FLOAT) / 
                            NULLIF(COUNT(pm.id), 0) as failure_rate
                        FROM providers p
                        INNER JOIN payments pm ON pm.provider_id = p.id
                        WHERE pm.created_at >= ? - INTERVAL '5 minutes'
                        GROUP BY p.id, p.name
                        HAVING COUNT(pm.id) >= ?
                    ) p
                    WHERE failure_rate > ?
                    ON CONFLICT DO NOTHING
                    """;

            return jdbcTemplate.update(sql,
                    detectedAt,
                    FAILURE_RATE_THRESHOLD,
                    detectedAt,
                    MIN_SAMPLE_SIZE,
                    FAILURE_RATE_THRESHOLD);

        } catch (Exception e) {
            log.error("Failed to detect high failure rate anomalies", e);
            return 0;
        }
    }

    private int detectSuccessRateDrop(Instant detectedAt) {
        try {
            // Compare last 5 minutes with previous 30 minutes
            String sql = """
                    INSERT INTO anomaly_events 
                    (id, entity_type, entity_id, anomaly_type, severity, description, detected_at, metadata)
                    SELECT 
                        gen_random_uuid(),
                        'PROVIDER',
                        provider_id::text,
                        'SUCCESS_RATE_DROP',
                        CASE 
                            WHEN rate_drop > 0.40 THEN 'CRITICAL'
                            WHEN rate_drop > 0.30 THEN 'HIGH'
                            ELSE 'MEDIUM'
                        END,
                        'Provider ' || provider_name || ' success rate dropped by ' || 
                        ROUND((rate_drop * 100)::numeric, 2) || '%',
                        ?,
                        jsonb_build_object(
                            'provider_name', provider_name,
                            'current_success_rate', current_success_rate,
                            'baseline_success_rate', baseline_success_rate,
                            'rate_drop', rate_drop,
                            'threshold', ?
                        )
                    FROM (
                        SELECT 
                            p.id as provider_id,
                            p.name as provider_name,
                            -- Current 5 minutes
                            COALESCE(
                                CAST(COUNT(CASE WHEN pm1.status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                                NULLIF(COUNT(pm1.id), 0),
                                0
                            ) as current_success_rate,
                            -- Baseline: previous 30 minutes
                            COALESCE(
                                CAST(COUNT(CASE WHEN pm2.status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                                NULLIF(COUNT(pm2.id), 0),
                                0
                            ) as baseline_success_rate,
                            COALESCE(
                                CAST(COUNT(CASE WHEN pm2.status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                                NULLIF(COUNT(pm2.id), 0),
                                0
                            ) - COALESCE(
                                CAST(COUNT(CASE WHEN pm1.status IN ('SETTLED', 'CAPTURED') THEN 1 END) AS FLOAT) / 
                                NULLIF(COUNT(pm1.id), 0),
                                0
                            ) as rate_drop,
                            COUNT(pm1.id) as current_count
                        FROM providers p
                        LEFT JOIN payments pm1 ON pm1.provider_id = p.id
                            AND pm1.created_at >= ? - INTERVAL '5 minutes'
                        LEFT JOIN payments pm2 ON pm2.provider_id = p.id
                            AND pm2.created_at >= ? - INTERVAL '35 minutes'
                            AND pm2.created_at < ? - INTERVAL '5 minutes'
                        GROUP BY p.id, p.name
                        HAVING COUNT(pm1.id) >= ?
                    ) comparison
                    WHERE rate_drop > ?
                    ON CONFLICT DO NOTHING
                    """;

            return jdbcTemplate.update(sql,
                    detectedAt,
                    SUCCESS_RATE_DROP_THRESHOLD,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    MIN_SAMPLE_SIZE,
                    SUCCESS_RATE_DROP_THRESHOLD);

        } catch (Exception e) {
            log.error("Failed to detect success rate drop anomalies", e);
            return 0;
        }
    }

    private int detectLatencySpikes(Instant detectedAt) {
        try {
            // Detect if average latency is 2x higher than baseline
            String sql = """
                    INSERT INTO anomaly_events 
                    (id, entity_type, entity_id, anomaly_type, severity, description, detected_at, metadata)
                    SELECT 
                        gen_random_uuid(),
                        'PROVIDER',
                        provider_id::text,
                        'LATENCY_SPIKE',
                        CASE 
                            WHEN latency_multiplier > 5 THEN 'CRITICAL'
                            WHEN latency_multiplier > 3 THEN 'HIGH'
                            ELSE 'MEDIUM'
                        END,
                        'Provider ' || provider_name || ' latency spiked to ' || 
                        ROUND(current_latency::numeric, 2) || 'ms (baseline: ' || 
                        ROUND(baseline_latency::numeric, 2) || 'ms)',
                        ?,
                        jsonb_build_object(
                            'provider_name', provider_name,
                            'current_latency_ms', current_latency,
                            'baseline_latency_ms', baseline_latency,
                            'latency_multiplier', latency_multiplier
                        )
                    FROM (
                        SELECT 
                            p.id as provider_id,
                            p.name as provider_name,
                            AVG(EXTRACT(EPOCH FROM (pm1.updated_at - pm1.created_at)) * 1000) as current_latency,
                            AVG(EXTRACT(EPOCH FROM (pm2.updated_at - pm2.created_at)) * 1000) as baseline_latency,
                            AVG(EXTRACT(EPOCH FROM (pm1.updated_at - pm1.created_at)) * 1000) /
                            NULLIF(AVG(EXTRACT(EPOCH FROM (pm2.updated_at - pm2.created_at)) * 1000), 0) as latency_multiplier,
                            COUNT(pm1.id) as current_count
                        FROM providers p
                        LEFT JOIN payments pm1 ON pm1.provider_id = p.id
                            AND pm1.created_at >= ? - INTERVAL '5 minutes'
                            AND pm1.updated_at IS NOT NULL
                        LEFT JOIN payments pm2 ON pm2.provider_id = p.id
                            AND pm2.created_at >= ? - INTERVAL '35 minutes'
                            AND pm2.created_at < ? - INTERVAL '5 minutes'
                            AND pm2.updated_at IS NOT NULL
                        GROUP BY p.id, p.name
                        HAVING COUNT(pm1.id) >= ?
                    ) latency_comparison
                    WHERE latency_multiplier > 2.0
                    ON CONFLICT DO NOTHING
                    """;

            return jdbcTemplate.update(sql,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    MIN_SAMPLE_SIZE);

        } catch (Exception e) {
            log.error("Failed to detect latency spike anomalies", e);
            return 0;
        }
    }

    private int detectVolumeAnomalies(Instant detectedAt) {
        try {
            // Detect unusual payment volume changes (50% drop or 200% increase)
            String sql = """
                    INSERT INTO anomaly_events 
                    (id, entity_type, entity_id, anomaly_type, severity, description, detected_at, metadata)
                    SELECT 
                        gen_random_uuid(),
                        'SYSTEM',
                        'global',
                        CASE 
                            WHEN volume_ratio < 0.5 THEN 'VOLUME_DROP'
                            ELSE 'VOLUME_SPIKE'
                        END,
                        CASE 
                            WHEN volume_ratio < 0.3 OR volume_ratio > 3.0 THEN 'CRITICAL'
                            WHEN volume_ratio < 0.5 OR volume_ratio > 2.0 THEN 'HIGH'
                            ELSE 'MEDIUM'
                        END,
                        'Payment volume ' || 
                        CASE 
                            WHEN volume_ratio < 1.0 THEN 'dropped by ' || ROUND((1 - volume_ratio) * 100, 2) || '%'
                            ELSE 'increased by ' || ROUND((volume_ratio - 1) * 100, 2) || '%'
                        END ||
                        ' (current: ' || current_volume || ', baseline: ' || baseline_volume || ')',
                        ?,
                        jsonb_build_object(
                            'current_volume', current_volume,
                            'baseline_volume', baseline_volume,
                            'volume_ratio', volume_ratio
                        )
                    FROM (
                        SELECT 
                            COUNT(CASE WHEN pm.created_at >= ? - INTERVAL '5 minutes' THEN 1 END) as current_volume,
                            COUNT(CASE 
                                WHEN pm.created_at >= ? - INTERVAL '35 minutes' 
                                AND pm.created_at < ? - INTERVAL '5 minutes' 
                                THEN 1 
                            END) / 6.0 as baseline_volume,
                            CAST(COUNT(CASE WHEN pm.created_at >= ? - INTERVAL '5 minutes' THEN 1 END) AS FLOAT) /
                            NULLIF(COUNT(CASE 
                                WHEN pm.created_at >= ? - INTERVAL '35 minutes' 
                                AND pm.created_at < ? - INTERVAL '5 minutes' 
                                THEN 1 
                            END) / 6.0, 0) as volume_ratio
                        FROM payments pm
                    ) volume_comparison
                    WHERE (volume_ratio < 0.5 OR volume_ratio > 2.0)
                      AND baseline_volume >= ?
                    ON CONFLICT DO NOTHING
                    """;

            return jdbcTemplate.update(sql,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    detectedAt,
                    MIN_SAMPLE_SIZE);

        } catch (Exception e) {
            log.error("Failed to detect volume anomalies", e);
            return 0;
        }
    }

    /**
     * Cleanup old anomaly events (keep last 90 days)
     */
    @Scheduled(cron = "0 50 2 * * *") // Daily at 02:50 AM
    public void cleanupOldAnomalies() {
        try {
            log.info("🧹 Cleaning up old anomaly events...");

            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'anomaly_events')",
                    Boolean.class
            );

            if (!tableExists) {
                log.warn("⚠️ anomaly_events table does not exist. Skipping cleanup.");
                return;
            }

            Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);

            String sql = "DELETE FROM anomaly_events WHERE detected_at < ?";
            int deleted = jdbcTemplate.update(sql, cutoff);

            log.info("✅ Deleted {} old anomaly events", deleted);

        } catch (Exception e) {
            log.error("❌ Failed to cleanup old anomaly events", e);
        }
    }
}

