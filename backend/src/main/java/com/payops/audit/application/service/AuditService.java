package com.payops.audit.application.service;

import com.payops.audit.application.port.input.AuditQueryUseCase;
import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.application.port.output.AuditRepository;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.StoredAuditEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService implements AuditTrail, AuditQueryUseCase {

    private static final int VERIFY_BATCH = 1000;

    private final AuditRepository repository;

    /**
     * Runs in its own transaction so the trail survives a rollback of the
     * calling operation (e.g. a rejected login still leaves a record).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        try {
            repository.appendChained(entry);
        } catch (RuntimeException ex) {
            log.error("Failed to write audit entry action={} actor={}", entry.action(), entry.actorEmail(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StoredAuditEntry> search(long organizationId, String action, PageQuery page) {
        return repository.search(organizationId, action, page);
    }

    @Override
    @Transactional(readOnly = true)
    public ChainVerification verifyChain(long organizationId) {
        long checked = 0;
        long afterId = 0;
        String expectedPrevious = null;
        while (true) {
            List<StoredAuditEntry> batch = repository.chronological(organizationId, afterId, VERIFY_BATCH);
            if (batch.isEmpty()) {
                return new ChainVerification(true, checked, null);
            }
            for (StoredAuditEntry stored : batch) {
                boolean linked = checked == 0 || String.valueOf(expectedPrevious).equals(String.valueOf(stored.previousHash()));
                if (!linked || !stored.isIntact()) {
                    return new ChainVerification(false, checked, stored.id());
                }
                expectedPrevious = stored.entryHash();
                afterId = stored.id();
                checked++;
            }
        }
    }
}
