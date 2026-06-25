package com.payops.payops360.audit.application.port.out;

import com.payops.payops360.audit.domain.model.AuditLog;

/**
 * OUTPUT PORT - Audit repository
 */
public interface AuditLogRepositoryPort {

    void save(AuditLog auditLog);
}

