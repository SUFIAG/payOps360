package com.payops.reconciliation.application.port.input;

/**
 * Background processing: the matching engine and the aged-payment sweep.
 */
public interface ReconciliationProcessingUseCase {

    /** Matches every pending line of the batch. Idempotent and safe to resume. */
    void processBatch(long organizationId, long batchId);

    /** Re-queues batches left RECEIVED, or stuck MATCHING after a restart. Returns how many were queued. */
    int requeueStalledBatches();

    /** Flags succeeded payments that no settlement has reported after the configured age. */
    int flagAgedUnsettledPayments();
}
