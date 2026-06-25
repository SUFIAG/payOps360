package com.payops.payops360.alert.adapter.out.persistence.mapper;

import com.payops.payops360.alert.adapter.out.persistence.entity.AlertEntity;
import com.payops.payops360.alert.domain.model.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct mapper for Alert persistence.
 */
@Mapper(componentModel = "spring")
public interface AlertPersistenceMapper {
    
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AlertEntity toEntity(Alert alert);
    
    Alert toDomain(AlertEntity entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "alertId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget AlertEntity entity, Alert alert);
}
