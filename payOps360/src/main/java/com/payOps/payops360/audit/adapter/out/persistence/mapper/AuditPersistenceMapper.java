package com.payops.payops360.audit.adapter.out.persistence.mapper;

import com.payops.payops360.audit.adapter.out.persistence.entity.AuditLogEntity;
import com.payops.payops360.audit.domain.model.AuditLog;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for Audit persistence.
 */
@Mapper(componentModel = "spring")
public interface AuditPersistenceMapper {

    AuditLogEntity toEntity(AuditLog auditLog);

    AuditLog toDomain(AuditLogEntity entity);
}

