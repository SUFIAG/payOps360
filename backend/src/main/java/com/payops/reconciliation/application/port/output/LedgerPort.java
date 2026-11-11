package com.payops.reconciliation.application.port.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Read access to the payment ledger and provider catalogue, shaped for
 * reconciliation. Lookups are tenant scoped.
 */
public interface LedgerPort {

    Optional<ProviderRef> provider(long organizationId, String providerCode);

    Optional<ProviderRef> providerById(long organizationId, long providerId);

    /** Payments whose PayOps, merchant or provider reference is one of {@code references}. */
    List<LedgerPayment> findByReferences(long organizationId, Collection<String> references);

    Optional<LedgerPayment> findByReference(long organizationId, String reference);

    Optional<LedgerPayment> findById(long organizationId, long paymentId);

    /**
     * Fuzzy candidates: same provider and currency, amount within [{@code min}, {@code max}],
     * created in [{@code from}, {@code to}], not yet matched to a SALE settlement line.
     */
    List<LedgerPayment> fuzzyCandidates(long organizationId, long providerId, String currency, BigDecimal min,
                                        BigDecimal max, Instant from, Instant to, int limit);

    /** Creation-time span of the payments matched by a batch, if any matched. */
    Optional<Window> matchedPaymentWindow(long batchId);

    /**
     * Succeeded payments of the provider created in [{@code from}, {@code to}] that no SALE
     * settlement line matched and that are not already flagged as missing.
     */
    List<LedgerPayment> unsettledPayments(long organizationId, long providerId, Instant from, Instant to, int limit);

    /** Providers with at least one completed batch, and where their settlement coverage starts. */
    List<Coverage> reconciledProviders();

    /** Recent succeeded payments of the provider not yet settled, newest first (sample generation). */
    List<LedgerPayment> recentSettleable(long organizationId, long providerId, Instant since, int limit);

    record ProviderRef(long id, String code, String name, BigDecimal costBps) {
    }

    record LedgerPayment(long id, String reference, String merchantReference, String providerReference,
                         long providerId, BigDecimal amount, String currency, String method, String status,
                         Instant createdAt) {
    }

    record Window(Instant from, Instant to) {
    }

    record Coverage(long organizationId, long providerId, Instant coverageStart) {
    }
}
