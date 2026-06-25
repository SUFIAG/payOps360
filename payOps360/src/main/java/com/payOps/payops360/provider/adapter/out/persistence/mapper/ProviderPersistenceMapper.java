package com.payops.payops360.provider.adapter.out.persistence.mapper;

import com.payops.payops360.provider.adapter.out.persistence.entity.ProviderEntity;
import com.payops.payops360.provider.adapter.out.persistence.entity.ProviderHealthEntity;
import com.payops.payops360.provider.domain.model.Provider;
import com.payops.payops360.provider.domain.model.ProviderHealth;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct mapper for Provider persistence.
 */
@Mapper(componentModel = "spring")
public interface ProviderPersistenceMapper {

    /**
     * Map domain Provider to entity
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProviderEntity toEntity(Provider provider);

    /**
     * Map entity to domain Provider
     */
    Provider toDomain(ProviderEntity entity);

    /**
     * Update entity from domain
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget ProviderEntity entity, Provider provider);

    /**
     * Map domain ProviderHealth to entity
     */
    ProviderHealthEntity toHealthEntity(ProviderHealth health);

    /**
     * Map entity to domain ProviderHealth
     */
    ProviderHealth toHealthDomain(ProviderHealthEntity entity);
}

