package com.payOps.payops360.ai.adapter.output.persistence.mapper;

import com.payOps.payops360.ai.adapter.output.persistence.entity.AIInvestigationEntity;
import com.payOps.payops360.ai.domain.model.AIInvestigation;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for AI Investigation Persistence
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AIInvestigationPersistenceMapper {
    AIInvestigationEntity toEntity(AIInvestigation investigation);
    AIInvestigation toDomain(AIInvestigationEntity entity);
}

