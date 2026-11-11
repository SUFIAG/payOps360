package com.payops.audit.application.port.in;

import com.payops.audit.domain.StoredAuditEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

public interface AuditQueryUseCase {

    PageResult<StoredAuditEntry> search(long organizationId, String action, PageQuery page);

    ChainVerification verifyChain(long organizationId);

    record ChainVerification(boolean intact, long entriesChecked, Long firstBrokenEntryId) {
    }
}
