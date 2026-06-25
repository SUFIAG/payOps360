package com.payOps.payops360.failure.adapter.output.persistence.mapper;

import com.payOps.payops360.failure.adapter.output.persistence.entity.FailureClassificationEntity;
import com.payOps.payops360.failure.domain.model.FailureClassification;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for Failure Classification Persistence
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FailureClassificationPersistenceMapper {
    FailureClassificationEntity toEntity(FailureClassification classification);
    FailureClassification toDomain(FailureClassificationEntity entity);
}

