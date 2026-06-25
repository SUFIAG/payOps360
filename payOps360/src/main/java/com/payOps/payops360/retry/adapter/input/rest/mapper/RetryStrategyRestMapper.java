package com.payOps.payops360.retry.adapter.input.rest.mapper;

import com.payOps.payops360.retry.adapter.input.rest.dto.RetryStrategyResponse;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for Retry Strategy REST DTOs
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RetryStrategyRestMapper {
    RetryStrategyResponse toResponse(RetryStrategy retryStrategy);
}

