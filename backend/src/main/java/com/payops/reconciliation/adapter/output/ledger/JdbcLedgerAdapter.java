package com.payops.reconciliation.adapter.output.ledger;

import com.payops.provider.application.port.input.ProviderCatalogUseCase;
import com.payops.provider.application.port.input.ProviderView;
import com.payops.reconciliation.application.port.output.LedgerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Read model over the payments table for reconciliation. Providers come from
 * the provider module's catalogue port; payment rows are read directly (the
 * same pattern the analytics and incident read models use).
 */
@Component
@RequiredArgsConstructor
class JdbcLedgerAdapter implements LedgerPort {

    /** Statuses in which the provider has captured funds that should eventually settle. */
    private static final String SETTLEABLE = "('CAPTURED', 'PROCESSING', 'SETTLED', 'REFUNDED', 'CHARGEBACK')";
    private static final String COLUMNS = "p.id, p.reference, p.merchant_reference, p.provider_reference, p.provider_id, "
            + "p.amount, p.currency, p.method, p.status, p.created_at";
    private static final String NOT_SETTLED = """
            NOT EXISTS (SELECT 1 FROM settlement_lines l WHERE l.payment_id = p.id AND l.type = 'SALE'
                        AND l.match_status IN ('MATCHED', 'AMOUNT_MISMATCH'))
            """;
    private static final String NOT_FLAGGED = """
            NOT EXISTS (SELECT 1 FROM reconciliation_exceptions e WHERE e.organization_id = p.organization_id
                        AND e.payment_id = p.id AND e.type = 'MISSING_IN_SETTLEMENT')
            """;

    private static final RowMapper<LedgerPayment> PAYMENT = (rs, i) -> new LedgerPayment(rs.getLong("id"),
            rs.getString("reference"), rs.getString("merchant_reference"), rs.getString("provider_reference"),
            rs.getLong("provider_id"), rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("method"),
            rs.getString("status"), rs.getTimestamp("created_at").toInstant());

    private final ProviderCatalogUseCase providers;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    @Override
    public Optional<ProviderRef> provider(long organizationId, String providerCode) {
        return providers.findByCode(organizationId, providerCode).map(JdbcLedgerAdapter::ref);
    }

    @Override
    public Optional<ProviderRef> providerById(long organizationId, long providerId) {
        return providers.findById(organizationId, providerId).map(JdbcLedgerAdapter::ref);
    }

    private static ProviderRef ref(ProviderView v) {
        return new ProviderRef(v.id(), v.code(), v.name(), v.costBps());
    }

    @Override
    public List<LedgerPayment> findByReferences(long organizationId, Collection<String> references) {
        if (references.isEmpty()) {
            return List.of();
        }
        return named.query("SELECT " + COLUMNS + " FROM payments p WHERE p.organization_id = :org "
                        + "AND (p.reference IN (:refs) OR p.merchant_reference IN (:refs) OR p.provider_reference IN (:refs))",
                new MapSqlParameterSource("org", organizationId).addValue("refs", references), PAYMENT);
    }

    @Override
    public Optional<LedgerPayment> findByReference(long organizationId, String reference) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payments p WHERE p.organization_id = ? "
                        + "AND (p.reference = ? OR p.merchant_reference = ? OR p.provider_reference = ?) "
                        + "ORDER BY (p.reference = ?) DESC, p.created_at DESC LIMIT 1",
                PAYMENT, organizationId, reference, reference, reference, reference).stream().findFirst();
    }

    @Override
    public Optional<LedgerPayment> findById(long organizationId, long paymentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payments p WHERE p.organization_id = ? AND p.id = ?",
                PAYMENT, organizationId, paymentId).stream().findFirst();
    }

    @Override
    public List<LedgerPayment> fuzzyCandidates(long organizationId, long providerId, String currency, BigDecimal min,
                                               BigDecimal max, Instant from, Instant to, int limit) {
        BigDecimal target = min.add(max).divide(BigDecimal.valueOf(2));
        return jdbc.query("SELECT " + COLUMNS + " FROM payments p WHERE p.organization_id = ? AND p.provider_id = ? "
                        + "AND p.currency = ? AND p.amount BETWEEN ? AND ? AND p.created_at BETWEEN ? AND ? "
                        + "AND p.status IN " + SETTLEABLE + " AND " + NOT_SETTLED
                        + " ORDER BY abs(p.amount - ?), p.created_at DESC LIMIT ?",
                PAYMENT, organizationId, providerId, currency, min, max, Timestamp.from(from), Timestamp.from(to),
                target, limit);
    }

    @Override
    public Optional<Window> matchedPaymentWindow(long batchId) {
        return jdbc.query("""
                SELECT min(p.created_at) AS first_created, max(p.created_at) AS last_created
                FROM settlement_lines l
                JOIN settlement_batches b ON b.id = l.batch_id
                JOIN payments p ON p.id = l.payment_id AND p.provider_id = b.provider_id
                WHERE l.batch_id = ? AND l.type = 'SALE' AND l.match_status IN ('MATCHED', 'AMOUNT_MISMATCH')
                """, (rs, i) -> rs.getTimestamp("first_created") == null ? null
                : new Window(rs.getTimestamp("first_created").toInstant(), rs.getTimestamp("last_created").toInstant()),
                batchId).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Override
    public List<LedgerPayment> unsettledPayments(long organizationId, long providerId, Instant from, Instant to, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payments p WHERE p.organization_id = ? AND p.provider_id = ? "
                        + "AND p.created_at BETWEEN ? AND ? AND p.status IN " + SETTLEABLE
                        + " AND " + NOT_SETTLED + " AND " + NOT_FLAGGED + " ORDER BY p.created_at LIMIT ?",
                PAYMENT, organizationId, providerId, Timestamp.from(from), Timestamp.from(to), limit);
    }

    @Override
    public List<Coverage> reconciledProviders() {
        return jdbc.query("""
                SELECT organization_id, provider_id, min(period_start) AS coverage_start FROM settlement_batches
                WHERE status = 'COMPLETED' AND period_start IS NOT NULL
                GROUP BY organization_id, provider_id
                """, (rs, i) -> new Coverage(rs.getLong("organization_id"), rs.getLong("provider_id"),
                rs.getTimestamp("coverage_start").toInstant()));
    }

    @Override
    public List<LedgerPayment> recentSettleable(long organizationId, long providerId, Instant since, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payments p WHERE p.organization_id = ? AND p.provider_id = ? "
                        + "AND p.created_at >= ? AND p.status IN " + SETTLEABLE + " AND " + NOT_SETTLED
                        + " AND " + NOT_FLAGGED + " ORDER BY p.created_at DESC LIMIT ?",
                PAYMENT, organizationId, providerId, Timestamp.from(since), limit);
    }
}
