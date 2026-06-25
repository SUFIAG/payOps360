package com.payOps.payops360.incident.adapter.output.persistence.mapper;

import com.payOps.payops360.incident.adapter.output.persistence.entity.IncidentEntity;
import com.payOps.payops360.incident.domain.model.Incident;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for Incident Persistence
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface IncidentPersistenceMapper {
    IncidentEntity toEntity(Incident incident);
    Incident toDomain(IncidentEntity entity);
}

