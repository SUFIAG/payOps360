package com.payops.reconciliation.application.port.out;

/**
 * Hands a batch to the asynchronous matching engine. Implementations submit
 * only after the current transaction commits, so the worker sees the lines.
 */
public interface MatchingQueue {

    void submit(long organizationId, long batchId);
}
