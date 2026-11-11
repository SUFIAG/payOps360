package com.payops.reconciliation.adapter.output.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.analytics.domain.FxRates;
import com.payops.reconciliation.application.port.input.BatchView;
import com.payops.reconciliation.application.port.input.ExceptionView;
import com.payops.reconciliation.application.port.input.ReconciliationQueryUseCase;
import com.payops.reconciliation.application.port.output.ReconciliationRepository;
import com.payops.reconciliation.domain.BatchStatus;
import com.payops.reconciliation.domain.ExceptionStatus;
import com.payops.reconciliation.domain.ExceptionType;
import com.payops.reconciliation.domain.FeeSchedule;
import com.payops.reconciliation.domain.MappingProfile;
import com.payops.reconciliation.domain.MatchMethod;
import com.payops.reconciliation.domain.MatchStatus;
import com.payops.reconciliation.domain.ResolutionReason;
import com.payops.reconciliation.domain.SettlementLineType;
import com.payops.reconciliation.domain.SettlementRecord;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
class JdbcReconciliationRepository implements ReconciliationRepository {

    private static final String SETTLED_STATUSES = "('MATCHED', 'AMOUNT_MISMATCH')";
    private static final String OPEN_STATUSES = "('OPEN', 'INVESTIGATING')";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper objectMapper;

    // ================================================================== batches

    @Override
    public long createBatch(long organizationId, long providerId, String reference, String fileName,
                            String provisionalChecksum, String uploadedBy, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO settlement_batches (reference, organization_id, provider_id, file_name, checksum, status,
                                                    uploaded_by, created_at)
                    VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, reference);
            ps.setLong(2, organizationId);
            ps.setLong(3, providerId);
            ps.setString(4, fileName);
            ps.setString(5, provisionalChecksum);
            ps.setString(6, uploadedBy);
            ps.setTimestamp(7, Timestamp.from(now));
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Override
    public void insertLines(long organizationId, long batchId, List<SettlementRecord> records) {
        jdbc.batchUpdate("""
                        INSERT INTO settlement_lines (batch_id, organization_id, line_no, type, provider_reference, amount,
                                                      currency, fee, fx_rate, settled_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                records.stream().map(r -> new Object[]{batchId, organizationId, r.lineNo(), r.type().name(),
                        r.reference(), r.amount(), r.currency(), r.fee(), r.fxRate(), Timestamp.from(r.settledAt())}).toList(),
                new int[]{Types.BIGINT, Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC,
                        Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP});
    }

    @Override
    public boolean checksumExists(long organizationId, String checksum) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM settlement_batches WHERE organization_id = ? AND checksum = ?)",
                Boolean.class, organizationId, checksum);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void finishImport(long batchId, String checksum, int totalLines, int rejectedLines, List<String> parseErrors) {
        try {
            jdbc.update("""
                    UPDATE settlement_batches b
                    SET checksum = ?, total_lines = ?, rejected_lines = ?, parse_errors = CAST(? AS jsonb),
                        gross_amount_usd = s.gross, fee_amount_usd = s.fees, period_start = s.first_settled,
                        period_end = s.last_settled
                    FROM (SELECT coalesce(sum(%s) FILTER (WHERE type <> 'FEE'), 0) AS gross,
                                 coalesce(sum(%s), 0) AS fees,
                                 min(settled_at) AS first_settled, max(settled_at) AS last_settled
                          FROM settlement_lines WHERE batch_id = ?) s
                    WHERE b.id = ?
                    """.formatted(FxRates.sqlToUsd("abs(amount)", "currency"), FxRates.sqlToUsd("coalesce(fee, 0)", "currency")),
                    checksum, totalLines, rejectedLines, toJson(parseErrors), batchId, batchId);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("DUPLICATE_SETTLEMENT_FILE", "This settlement file has already been imported");
        }
    }

    @Override
    public Optional<BatchKey> batchKey(long organizationId, String reference) {
        return jdbc.query("SELECT id, organization_id, provider_id, reference FROM settlement_batches "
                + "WHERE organization_id = ? AND reference = ?", BATCH_KEY, organizationId, reference).stream().findFirst();
    }

    @Override
    public Optional<BatchKey> batchKey(long batchId) {
        return jdbc.query("SELECT id, organization_id, provider_id, reference FROM settlement_batches WHERE id = ?",
                BATCH_KEY, batchId).stream().findFirst();
    }

    private static final RowMapper<BatchKey> BATCH_KEY = (rs, i) -> new BatchKey(rs.getLong("id"),
            rs.getLong("organization_id"), rs.getLong("provider_id"), rs.getString("reference"));

    private static final String BATCH_SELECT = """
            SELECT b.*, p.code AS provider_code, p.name AS provider_name
            FROM settlement_batches b JOIN providers p ON p.id = b.provider_id
            """;

    @Override
    public Optional<BatchView> batch(long organizationId, long batchId) {
        return jdbc.query(BATCH_SELECT + " WHERE b.organization_id = ? AND b.id = ?", this::batchView, organizationId, batchId)
                .stream().findFirst();
    }

    @Override
    public PageResult<BatchView> batches(long organizationId, Long providerId, PageQuery page) {
        String where = " WHERE b.organization_id = ? AND (CAST(? AS BIGINT) IS NULL OR b.provider_id = ?)";
        Long total = jdbc.queryForObject("SELECT count(*) FROM settlement_batches b" + where, Long.class,
                organizationId, providerId, providerId);
        List<BatchView> items = jdbc.query(BATCH_SELECT + where + " ORDER BY b.created_at DESC LIMIT ? OFFSET ?",
                this::batchView, organizationId, providerId, providerId, page.size(), (long) page.page() * page.size());
        return new PageResult<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    private BatchView batchView(ResultSet rs, int i) throws SQLException {
        int total = rs.getInt("total_lines");
        int rejected = rs.getInt("rejected_lines");
        int processed = rs.getInt("processed_lines");
        int matched = rs.getInt("matched_count");
        int mismatch = rs.getInt("mismatch_count");
        int missingInLedger = rs.getInt("missing_in_ledger");
        int duplicates = rs.getInt("duplicate_count");
        BatchStatus status = BatchStatus.valueOf(rs.getString("status"));
        int valid = Math.max(0, total - rejected);
        int progress = status == BatchStatus.COMPLETED ? 100 : valid == 0 ? 0 : Math.min(100, processed * 100 / valid);
        int reconcilable = matched + mismatch + missingInLedger + duplicates;
        BigDecimal matchRate = reconcilable == 0 ? null
                : BigDecimal.valueOf(matched * 100.0 / reconcilable).setScale(2, RoundingMode.HALF_UP);
        return new BatchView(rs.getString("reference"), rs.getString("provider_code"), rs.getString("provider_name"),
                rs.getString("file_name"), status, total, rejected, processed, progress, matched, mismatch,
                missingInLedger, rs.getInt("missing_in_settlement"), duplicates, rs.getInt("fee_overcharge_count"),
                rs.getInt("exception_count"), matchRate, rs.getBigDecimal("gross_amount_usd"),
                rs.getBigDecimal("fee_amount_usd"), instant(rs, "period_start"), instant(rs, "period_end"),
                fromJson(rs.getString("parse_errors")), rs.getString("error_message"), rs.getString("uploaded_by"),
                instant(rs, "created_at"), instant(rs, "started_at"), instant(rs, "completed_at"));
    }

    @Override
    public boolean claimBatch(long batchId, Instant now, Instant staleBefore) {
        return jdbc.update("""
                UPDATE settlement_batches
                SET status = 'MATCHING', started_at = ?, error_message = NULL,
                    processed_lines = (SELECT count(*) FROM settlement_lines l
                                       WHERE l.batch_id = settlement_batches.id AND l.match_status <> 'PENDING')
                WHERE id = ? AND (status = 'RECEIVED' OR (status = 'MATCHING' AND started_at < ?))
                """, Timestamp.from(now), batchId, Timestamp.from(staleBefore)) == 1;
    }

    @Override
    public void updateProgress(long batchId, int processedLines) {
        jdbc.update("UPDATE settlement_batches SET processed_lines = processed_lines + ? WHERE id = ?", processedLines, batchId);
    }

    @Override
    public void completeBatch(long batchId, Instant now) {
        jdbc.update("""
                UPDATE settlement_batches b
                SET matched_count = s.matched, mismatch_count = s.mismatch, missing_in_ledger = s.missing_ledger,
                    duplicate_count = s.duplicates, processed_lines = s.processed,
                    missing_in_settlement = e.missing_settlement, fee_overcharge_count = e.fee_overcharges,
                    exception_count = e.total,
                    period_start = coalesce(w.first_created, b.period_start),
                    period_end = coalesce(w.last_created, b.period_end),
                    status = 'COMPLETED', completed_at = ?
                FROM (SELECT count(*) FILTER (WHERE match_status = 'MATCHED') AS matched,
                             count(*) FILTER (WHERE match_status = 'AMOUNT_MISMATCH') AS mismatch,
                             count(*) FILTER (WHERE match_status = 'MISSING_IN_LEDGER') AS missing_ledger,
                             count(*) FILTER (WHERE match_status = 'DUPLICATE') AS duplicates,
                             count(*) FILTER (WHERE match_status <> 'PENDING') AS processed
                      FROM settlement_lines WHERE batch_id = ?) s,
                     (SELECT count(*) FILTER (WHERE type = 'MISSING_IN_SETTLEMENT') AS missing_settlement,
                             count(*) FILTER (WHERE type = 'FEE_OVERCHARGE') AS fee_overcharges,
                             count(*) AS total
                      FROM reconciliation_exceptions WHERE batch_id = ?) e,
                     (SELECT min(p.created_at) AS first_created, max(p.created_at) AS last_created
                      FROM settlement_lines l
                      JOIN settlement_batches lb ON lb.id = l.batch_id
                      JOIN payments p ON p.id = l.payment_id AND p.provider_id = lb.provider_id
                      WHERE l.batch_id = ? AND l.type = 'SALE' AND l.match_status IN %s) w
                WHERE b.id = ?
                """.formatted(SETTLED_STATUSES), Timestamp.from(now), batchId, batchId, batchId, batchId);
    }

    @Override
    public void failBatch(long batchId, String message, Instant now) {
        jdbc.update("UPDATE settlement_batches SET status = 'FAILED', error_message = ?, completed_at = ? WHERE id = ?",
                message.length() > 500 ? message.substring(0, 500) : message, Timestamp.from(now), batchId);
    }

    @Override
    public List<BatchKey> stalledBatches(Instant receivedBefore, Instant matchingStartedBefore) {
        return jdbc.query("""
                SELECT id, organization_id, provider_id, reference FROM settlement_batches
                WHERE (status = 'RECEIVED' AND created_at < ?) OR (status = 'MATCHING' AND started_at < ?)
                ORDER BY created_at LIMIT 20
                """, BATCH_KEY, Timestamp.from(receivedBefore), Timestamp.from(matchingStartedBefore));
    }

    // ================================================================== lines

    private static final RowMapper<LineRecord> LINE = (rs, i) -> new LineRecord(rs.getLong("id"), rs.getLong("batch_id"),
            rs.getInt("line_no"), SettlementLineType.valueOf(rs.getString("type")), rs.getString("provider_reference"),
            rs.getBigDecimal("amount"), rs.getString("currency"), rs.getBigDecimal("fee"), rs.getBigDecimal("expected_fee"),
            rs.getBigDecimal("fx_rate"), instant(rs, "settled_at"), MatchStatus.valueOf(rs.getString("match_status")),
            rs.getString("match_method") == null ? null : MatchMethod.valueOf(rs.getString("match_method")),
            (Long) rs.getObject("payment_id", Long.class));

    @Override
    public List<LineRecord> pendingLines(long batchId, long afterId, int limit) {
        return jdbc.query("SELECT * FROM settlement_lines WHERE batch_id = ? AND id > ? AND match_status = 'PENDING' "
                + "ORDER BY id LIMIT ?", LINE, batchId, afterId, limit);
    }

    @Override
    public void updateLines(List<LineUpdate> updates) {
        jdbc.batchUpdate("""
                        UPDATE settlement_lines SET match_status = ?, match_method = ?, payment_id = ?, expected_fee = ?
                        WHERE id = ?
                        """,
                updates.stream().map(u -> new Object[]{u.status().name(), u.method() == null ? null : u.method().name(),
                        u.paymentId(), u.expectedFee(), u.lineId()}).toList(),
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.NUMERIC, Types.BIGINT});
    }

    @Override
    public Set<String> settledPaymentKeys(Collection<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        named.query("SELECT payment_id, type FROM settlement_lines WHERE payment_id IN (:ids) AND match_status IN "
                        + SETTLED_STATUSES, new MapSqlParameterSource("ids", paymentIds),
                rs -> {
                    keys.add(rs.getLong("payment_id") + ":" + rs.getString("type"));
                });
        return keys;
    }

    @Override
    public Optional<LineRecord> line(long lineId) {
        return jdbc.query("SELECT * FROM settlement_lines WHERE id = ?", LINE, lineId).stream().findFirst();
    }

    // ================================================================== exceptions

    @Override
    public int insertExceptions(List<NewException> exceptions) {
        if (exceptions.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate("""
                        INSERT INTO reconciliation_exceptions (reference, organization_id, provider_id, batch_id, line_id,
                            payment_id, type, status, amount_at_risk, currency, expected_amount, actual_amount,
                            payment_reference, provider_reference, detail, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """, exceptions, 500,
                (ps, e) -> {
                    Timestamp at = Timestamp.from(e.createdAt());
                    ps.setString(1, e.reference());
                    ps.setLong(2, e.organizationId());
                    ps.setLong(3, e.providerId());
                    ps.setObject(4, e.batchId(), Types.BIGINT);
                    ps.setObject(5, e.lineId(), Types.BIGINT);
                    ps.setObject(6, e.paymentId(), Types.BIGINT);
                    ps.setString(7, e.type().name());
                    ps.setBigDecimal(8, e.amountAtRisk());
                    ps.setString(9, e.currency());
                    ps.setBigDecimal(10, e.expectedAmount());
                    ps.setBigDecimal(11, e.actualAmount());
                    ps.setString(12, e.paymentReference());
                    ps.setString(13, e.providerReference());
                    ps.setString(14, e.detail());
                    ps.setTimestamp(15, at);
                    ps.setTimestamp(16, at);
                });
        int inserted = 0;
        for (int[] chunk : counts) {
            for (int c : chunk) {
                inserted += c == Statement.SUCCESS_NO_INFO ? 1 : Math.max(c, 0);
            }
        }
        return inserted;
    }

    private static final RowMapper<ExceptionState> EXCEPTION_STATE = (rs, i) -> new ExceptionState(rs.getLong("id"),
            rs.getLong("organization_id"), rs.getLong("provider_id"), rs.getObject("batch_id", Long.class),
            rs.getObject("line_id", Long.class), rs.getObject("payment_id", Long.class),
            ExceptionType.valueOf(rs.getString("type")), ExceptionStatus.valueOf(rs.getString("status")),
            rs.getString("assignee"),
            rs.getString("resolution_reason") == null ? null : ResolutionReason.valueOf(rs.getString("resolution_reason")),
            rs.getString("resolution_note"), rs.getString("resolved_by"), instant(rs, "resolved_at"), rs.getLong("version"));

    @Override
    public Optional<ExceptionState> exceptionState(long organizationId, String reference) {
        return jdbc.query("SELECT * FROM reconciliation_exceptions WHERE organization_id = ? AND reference = ?",
                EXCEPTION_STATE, organizationId, reference).stream().findFirst();
    }

    private static final String EXCEPTION_SELECT = """
            SELECT e.*, p.code AS provider_code, p.name AS provider_name, b.reference AS batch_reference,
                   floor(extract(epoch FROM (now() - e.created_at)) / 3600)::bigint AS age_hours
            FROM reconciliation_exceptions e
            JOIN providers p ON p.id = e.provider_id
            LEFT JOIN settlement_batches b ON b.id = e.batch_id
            """;

    private static final RowMapper<ExceptionView> EXCEPTION_VIEW = (rs, i) -> {
        BigDecimal atRisk = rs.getBigDecimal("amount_at_risk");
        String currency = rs.getString("currency");
        return new ExceptionView(rs.getString("reference"), ExceptionType.valueOf(rs.getString("type")),
                ExceptionStatus.valueOf(rs.getString("status")), rs.getString("provider_code"),
                rs.getString("provider_name"), rs.getString("batch_reference"), rs.getString("payment_reference"),
                rs.getString("provider_reference"), atRisk, currency, FxRates.toUsd(atRisk, currency),
                rs.getBigDecimal("expected_amount"), rs.getBigDecimal("actual_amount"), rs.getString("detail"),
                rs.getString("assignee"),
                rs.getString("resolution_reason") == null ? null : ResolutionReason.valueOf(rs.getString("resolution_reason")),
                rs.getString("resolution_note"), rs.getString("resolved_by"), instant(rs, "resolved_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"), Math.max(0, rs.getLong("age_hours")));
    };

    @Override
    public Optional<ExceptionView> exception(long organizationId, long exceptionId) {
        return jdbc.query(EXCEPTION_SELECT + " WHERE e.organization_id = ? AND e.id = ?", EXCEPTION_VIEW,
                organizationId, exceptionId).stream().findFirst();
    }

    @Override
    public PageResult<ExceptionView> exceptions(long organizationId, ExceptionQuery query, PageQuery page) {
        StringBuilder where = new StringBuilder(" WHERE e.organization_id = :org");
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizationId);
        if (query.types() != null && !query.types().isEmpty()) {
            where.append(" AND e.type IN (:types)");
            params.addValue("types", query.types().stream().map(Enum::name).toList());
        }
        if (query.statuses() != null && !query.statuses().isEmpty()) {
            where.append(" AND e.status IN (:statuses)");
            params.addValue("statuses", query.statuses().stream().map(Enum::name).toList());
        }
        if (query.providerId() != null) {
            where.append(" AND e.provider_id = :provider");
            params.addValue("provider", query.providerId());
        }
        if (query.batchId() != null) {
            where.append(" AND e.batch_id = :batch");
            params.addValue("batch", query.batchId());
        }
        if (query.assignee() != null && !query.assignee().isBlank()) {
            where.append(" AND lower(e.assignee) = lower(:assignee)");
            params.addValue("assignee", query.assignee().trim());
        }
        Long total = named.queryForObject("SELECT count(*) FROM reconciliation_exceptions e" + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", (long) page.page() * page.size());
        List<ExceptionView> items = named.query(EXCEPTION_SELECT + where
                + " ORDER BY e.created_at DESC, e.id DESC LIMIT :limit OFFSET :offset", params, EXCEPTION_VIEW);
        return new PageResult<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    @Override
    public boolean updateException(ExceptionState s, Instant now) {
        return jdbc.update("""
                UPDATE reconciliation_exceptions
                SET status = ?, assignee = ?, resolution_reason = ?, resolution_note = ?, resolved_by = ?, resolved_at = ?,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND organization_id = ? AND version = ?
                """, s.status().name(), s.assignee(), s.resolutionReason() == null ? null : s.resolutionReason().name(),
                s.resolutionNote(), s.resolvedBy(), s.resolvedAt() == null ? null : Timestamp.from(s.resolvedAt()),
                Timestamp.from(now), s.id(), s.organizationId(), s.version()) == 1;
    }

    @Override
    public int resolveMissingInSettlement(long organizationId, Collection<Long> paymentIds, String note, Instant now) {
        if (paymentIds.isEmpty()) {
            return 0;
        }
        return named.update("""
                UPDATE reconciliation_exceptions
                SET status = 'RESOLVED', resolution_reason = 'SETTLED_LATER', resolution_note = :note,
                    resolved_by = 'system', resolved_at = :now, updated_at = :now, version = version + 1
                WHERE organization_id = :org AND type = 'MISSING_IN_SETTLEMENT' AND status IN %s
                  AND payment_id IN (:ids)
                """.formatted(OPEN_STATUSES), new MapSqlParameterSource("note", note)
                .addValue("now", Timestamp.from(now)).addValue("org", organizationId).addValue("ids", paymentIds));
    }

    @Override
    public ReconciliationQueryUseCase.Summary summary(long organizationId, int days, Instant since) {
        Timestamp from = Timestamp.from(since);
        long[] lines = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE match_status IN ('MATCHED', 'AMOUNT_MISMATCH', 'MISSING_IN_LEDGER', 'DUPLICATE')) AS reconciled,
                       count(*) FILTER (WHERE match_status = 'MATCHED') AS matched
                FROM settlement_lines WHERE organization_id = ? AND settled_at >= ? AND type <> 'FEE'
                """, (rs, i) -> new long[]{rs.getLong("reconciled"), rs.getLong("matched")}, organizationId, from);
        Long batches = jdbc.queryForObject("SELECT count(*) FROM settlement_batches WHERE organization_id = ? AND created_at >= ?",
                Long.class, organizationId, from);
        String usd = FxRates.sqlToUsd("amount_at_risk", "currency");
        Map<ExceptionType, Long> openByType = new EnumMap<>(ExceptionType.class);
        BigDecimal[] atRisk = {BigDecimal.ZERO};
        jdbc.query("SELECT type, count(*) AS n, coalesce(sum(" + usd + "), 0) AS usd FROM reconciliation_exceptions "
                        + "WHERE organization_id = ? AND status IN " + OPEN_STATUSES + " GROUP BY type",
                rs -> {
                    openByType.put(ExceptionType.valueOf(rs.getString("type")), rs.getLong("n"));
                    atRisk[0] = atRisk[0].add(rs.getBigDecimal("usd"));
                }, organizationId);
        BigDecimal leakage = jdbc.queryForObject("SELECT coalesce(sum(" + usd + "), 0) FROM reconciliation_exceptions "
                        + "WHERE organization_id = ? AND type = 'FEE_OVERCHARGE' AND created_at >= ? "
                        + "AND coalesce(resolution_reason, '') <> 'FALSE_POSITIVE'",
                BigDecimal.class, organizationId, from);
        long open = openByType.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal matchRate = lines[0] == 0 ? null
                : BigDecimal.valueOf(lines[1] * 100.0 / lines[0]).setScale(2, RoundingMode.HALF_UP);
        return new ReconciliationQueryUseCase.Summary(days, matchRate, lines[0], lines[1], batches == null ? 0 : batches,
                open, atRisk[0].setScale(2, RoundingMode.HALF_UP),
                (leakage == null ? BigDecimal.ZERO : leakage).setScale(2, RoundingMode.HALF_UP), openByType);
    }

    @Override
    public List<ReconciliationQueryUseCase.TrendPoint> trend(long organizationId, Instant since) {
        return jdbc.query("""
                SELECT (settled_at AT TIME ZONE 'UTC')::date AS day,
                       count(*) FILTER (WHERE match_status <> 'PENDING') AS lines,
                       count(*) FILTER (WHERE match_status = 'MATCHED') AS matched
                FROM settlement_lines
                WHERE organization_id = ? AND settled_at >= ? AND type <> 'FEE'
                GROUP BY 1 ORDER BY 1
                """, (rs, i) -> {
            long total = rs.getLong("lines");
            long matched = rs.getLong("matched");
            return new ReconciliationQueryUseCase.TrendPoint(rs.getDate("day").toLocalDate(), total, matched,
                    total == 0 ? null : BigDecimal.valueOf(matched * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
        }, organizationId, Timestamp.from(since));
    }

    // ================================================================== configuration

    @Override
    public Optional<StoredFeeSchedule> feeSchedule(long organizationId, long providerId) {
        List<FeeSchedule.Rule> rules = new ArrayList<>();
        String[] updatedBy = {null};
        Instant[] updatedAt = {null};
        jdbc.query("""
                SELECT method, currency, percentage, fixed_fee, updated_by, updated_at FROM provider_fee_schedules
                WHERE organization_id = ? AND provider_id = ? ORDER BY method NULLS FIRST, currency NULLS FIRST
                """, rs -> {
            rules.add(new FeeSchedule.Rule(rs.getString("method"), rs.getString("currency"),
                    rs.getBigDecimal("percentage"), rs.getBigDecimal("fixed_fee")));
            Instant at = instant(rs, "updated_at");
            if (updatedAt[0] == null || at.isAfter(updatedAt[0])) {
                updatedAt[0] = at;
                updatedBy[0] = rs.getString("updated_by");
            }
        }, organizationId, providerId);
        return rules.isEmpty() ? Optional.empty() : Optional.of(new StoredFeeSchedule(rules, updatedBy[0], updatedAt[0]));
    }

    @Override
    public void replaceFeeSchedule(long organizationId, long providerId, List<FeeSchedule.Rule> rules, String actor,
                                   Instant now) {
        jdbc.update("DELETE FROM provider_fee_schedules WHERE organization_id = ? AND provider_id = ?", organizationId, providerId);
        jdbc.batchUpdate("""
                        INSERT INTO provider_fee_schedules (organization_id, provider_id, method, currency, percentage,
                                                            fixed_fee, updated_by, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rules.stream().map(r -> new Object[]{organizationId, providerId, r.method(), r.currency(), r.percentage(),
                        r.fixedFee(), actor, Timestamp.from(now)}).toList(),
                new int[]{Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC,
                        Types.VARCHAR, Types.TIMESTAMP});
    }

    @Override
    public Optional<StoredMapping> mapping(long organizationId, long providerId) {
        return jdbc.query("""
                SELECT delimiter, date_format, columns, updated_by, updated_at FROM settlement_mapping_profiles
                WHERE organization_id = ? AND provider_id = ?
                """, (rs, i) -> {
            Map<String, String> raw = readMap(rs.getString("columns"));
            Map<MappingProfile.Field, String> columns = new EnumMap<>(MappingProfile.Field.class);
            raw.forEach((k, v) -> {
                try {
                    columns.put(MappingProfile.Field.valueOf(k), v);
                } catch (IllegalArgumentException ignored) {
                    // unknown field from an older version: ignore
                }
            });
            return new StoredMapping(new MappingProfile(rs.getString("delimiter").charAt(0), rs.getString("date_format"),
                    columns), rs.getString("updated_by"), instant(rs, "updated_at"));
        }, organizationId, providerId).stream().findFirst();
    }

    @Override
    public void saveMapping(long organizationId, long providerId, MappingProfile profile, String actor, Instant now) {
        Map<String, String> columns = new LinkedHashMap<>();
        profile.columns().forEach((k, v) -> columns.put(k.name(), v));
        jdbc.update("""
                INSERT INTO settlement_mapping_profiles (organization_id, provider_id, delimiter, date_format, columns,
                                                         updated_by, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                ON CONFLICT (organization_id, provider_id) DO UPDATE SET delimiter = EXCLUDED.delimiter,
                    date_format = EXCLUDED.date_format, columns = EXCLUDED.columns, updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at
                """, organizationId, providerId, String.valueOf(profile.delimiter()), profile.dateFormat(),
                toJson(columns), actor, Timestamp.from(now));
    }

    // ================================================================== helpers

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, String> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
