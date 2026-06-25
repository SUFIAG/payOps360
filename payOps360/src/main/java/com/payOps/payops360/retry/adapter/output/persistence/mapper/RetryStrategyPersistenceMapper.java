package com.payOps.payops360.retry.adapter.output.persistence.mapper;

import com.payOps.payops360.retry.adapter.output.persistence.entity.RetryStrategyEntity;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for Retry Strategy Persistence
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RetryStrategyPersistenceMapper {
    RetryStrategyEntity toEntity(RetryStrategy retryStrategy);
    RetryStrategy toDomain(RetryStrategyEntity entity);
}

