package com.payops.payops360.audit.application.service;

import com.payops.payops360.audit.application.port.in.LogAuditEntryUseCase;
import com.payops.payops360.audit.application.port.out.AuditLogRepositoryPort;
import com.payops.payops360.audit.domain.model.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Audit Application Service
 *
 * Handles audit logging (asynchronously for performance).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService implements LogAuditEntryUseCase {

    private final AuditLogRepositoryPort auditRepository;

    /**
     * Log audit entry asynchronously
     * Uses REQUIRES_NEW to ensure audit is saved even if main transaction rolls back
     */
    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(LogAuditCommand command) {
        try {
            log.debug("Logging audit: action={}, entity={}:{}",
                    command.action(), command.entityType(), command.entityId());

            AuditLog auditLog = AuditLog.builder()
                    .action(command.action())
                    .entityType(command.entityType())
                    .entityId(command.entityId())
                    .userId(command.userId())
                    .ipAddress(command.ipAddress())
                    .userAgent(command.userAgent())
                    .oldValues(command.oldValues() != null ? command.oldValues() : java.util.Map.of())
                    .newValues(command.newValues() != null ? command.newValues() : java.util.Map.of())
                    .requestId(command.requestId())
                    .sessionId(command.sessionId())
                    .occurredAt(Instant.now())
                    .build();

            auditRepository.save(auditLog);

        } catch (Exception e) {
            // Never let audit logging crash the main transaction
            log.error("Failed to log audit entry", e);
        }
    }
}

