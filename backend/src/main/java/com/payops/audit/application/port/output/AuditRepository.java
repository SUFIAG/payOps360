package com.payops.audit.application.port.output;

import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.StoredAuditEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.util.List;

public interface AuditRepository {

    /**
     * Appends the entry to its tenant's hash chain atomically
     * (implementations must serialise appends per tenant).
     */
    void appendChained(AuditEntry entry);

    PageResult<StoredAuditEntry> search(long organizationId, String action, PageQuery page);

    List<StoredAuditEntry> chronological(long organizationId, long afterId, int limit);
}
