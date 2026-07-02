package com.payops.payops360.provider.adapter.in.rest.mapper;

import com.payops.payops360.provider.adapter.in.rest.dto.ProviderHealthResponse;
import com.payops.payops360.provider.adapter.in.rest.dto.ProviderResponse;
import com.payops.payops360.provider.adapter.in.rest.dto.RegisterProviderRequest;
import com.payops.payops360.provider.application.port.in.RegisterProviderUseCase;
import com.payops.payops360.provider.domain.model.Provider;
import com.payops.payops360.provider.domain.model.ProviderHealth;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Provider REST DTOs.
 */
@Mapper(componentModel = "spring")
public interface ProviderRestMapper {

    /**
     * Map REST request to command
     */
    RegisterProviderUseCase.RegisterProviderCommand toCommand(RegisterProviderRequest request);

    /**
     * Map domain Provider to response
     */
    ProviderResponse toResponse(Provider provider);

    /**
     * Map domain ProviderHealth to response
     */
    @Mapping(target = "healthScore", expression = "java(health.getHealthScore())")
    @Mapping(target = "recommendedAction", expression = "java(health.getRecommendedAction())")
    ProviderHealthResponse toHealthResponse(ProviderHealth health);
}

