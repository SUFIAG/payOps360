package com.payops.payops360.audit.application.port.in;

import com.payops.payops360.audit.domain.model.AuditAction;
import com.payops.payops360.audit.domain.model.AuditLog;

import java.util.Map;

/**
 * INPUT PORT - Log audit entry use case
 */
public interface LogAuditEntryUseCase {

    void log(LogAuditCommand command);

    record LogAuditCommand(
            AuditAction action,
            String entityType,
            String entityId,
            String userId,
            String ipAddress,
            String userAgent,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            String requestId,
            String sessionId
    ) {
        public LogAuditCommand {
            if (action == null) {
                throw new IllegalArgumentException("Action cannot be null");
            }
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalArgumentException("Entity type cannot be null or empty");
            }
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalArgumentException("Entity ID cannot be null or empty");
            }
        }
    }
}

