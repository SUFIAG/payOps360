package com.payops.reconciliation.adapter.output.queue;

import com.payops.reconciliation.application.port.input.ReconciliationProcessingUseCase;
import com.payops.reconciliation.application.port.output.MatchingQueue;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process matching queue: a small bounded worker pool. Batches lost to a
 * restart stay RECEIVED/MATCHING in the database and are re-queued by the
 * reconciliation sweep job, so the queue itself needs no durability.
 */
@Slf4j
@Component
class ExecutorMatchingQueue implements MatchingQueue {

    private final ObjectProvider<ReconciliationProcessingUseCase> processing;
    private final ExecutorService workers;

    ExecutorMatchingQueue(ObjectProvider<ReconciliationProcessingUseCase> processing) {
        this.processing = processing;
        AtomicInteger counter = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "recon-matching-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void submit(long organizationId, long batchId) {
        Runnable task = () -> {
            try {
                processing.getObject().processBatch(organizationId, batchId);
            } catch (RuntimeException e) {
                log.error("Settlement batch {} could not be processed", batchId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(task, batchId);
                }
            });
        } else {
            enqueue(task, batchId);
        }
    }

    private void enqueue(Runnable task, long batchId) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("Matching queue is shut down; batch {} will be picked up by the sweep job", batchId);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        workers.shutdown();
        workers.awaitTermination(10, TimeUnit.SECONDS);
    }
}
