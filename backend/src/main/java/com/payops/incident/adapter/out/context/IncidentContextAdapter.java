package com.payops.incident.adapter.out.context;

import com.payops.incident.application.port.out.IncidentContextPort;
import com.payops.provider.application.port.in.ProviderCatalogUseCase;
import com.payops.provider.application.port.in.ProviderView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class IncidentContextAdapter implements IncidentContextPort {

    private final ProviderCatalogUseCase providers;
    private final JdbcTemplate jdbc;

    @Override
    public Optional<String> providerCode(long organizationId, long providerId) {
        return providers.findById(organizationId, providerId).map(ProviderView::code);
    }

    @Override
    public Optional<Long> providerId(long organizationId, String providerCode) {
        return providers.findByCode(organizationId, providerCode).map(ProviderView::id);
    }

    @Override
    public Impact impactSince(long organizationId, Long providerId, Instant since) {
        return jdbc.queryForObject("""
                SELECT count(*) AS payments, coalesce(sum(amount), 0) AS amount FROM payments
                WHERE organization_id = ? AND created_at >= ?
                  AND (CAST(? AS BIGINT) IS NULL OR provider_id = ?)
                  AND (status IN ('FAILED', 'RETRY_FAILED') OR stuck)
                """,
                (rs, i) -> new Impact(rs.getInt("payments"), rs.getBigDecimal("amount").setScale(2, RoundingMode.HALF_UP)),
                organizationId, Timestamp.from(since), providerId, providerId);
    }
}
