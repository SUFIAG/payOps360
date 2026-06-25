package com.payops.payops360.audit.adapter.out.persistence;

import com.payops.payops360.audit.adapter.out.persistence.entity.AuditLogEntity;
import com.payops.payops360.audit.adapter.out.persistence.mapper.AuditPersistenceMapper;
import com.payops.payops360.audit.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.payops.payops360.audit.application.port.out.AuditLogRepositoryPort;
import com.payops.payops360.audit.domain.model.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Audit Persistence Adapter - OUTPUT ADAPTER
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPersistenceAdapter implements AuditLogRepositoryPort {

    private final AuditLogJpaRepository jpaRepository;
    private final AuditPersistenceMapper mapper;

    @Override
    public void save(AuditLog auditLog) {
        log.debug("Saving audit log: action={}, entity={}:{}",
                auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId());

        AuditLogEntity entity = mapper.toEntity(auditLog);
        jpaRepository.save(entity);
    }
}

