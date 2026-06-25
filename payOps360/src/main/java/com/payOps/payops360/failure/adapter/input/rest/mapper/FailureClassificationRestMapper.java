package com.payOps/payops360.failure.adapter.input.rest.mapper;

import com.payOps/payops360.failure.adapter.input.rest.dto.FailureClassificationResponse;
import com.payOps/payops360.failure.domain.model.FailureClassification;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for Failure Classification REST DTOs
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FailureClassificationRestMapper {
    FailureClassificationResponse toResponse(FailureClassification classification);
}

