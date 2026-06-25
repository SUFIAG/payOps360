package com.payOps.payops360.incident.adapter.input.rest.mapper;

import com.payOps.payops360.incident.adapter.input.rest.dto.IncidentResponse;
import com.payOps/payops360.incident.domain.model.Incident;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct Mapper for Incident REST DTOs
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface IncidentRestMapper {

    @Mapping(target = "durationMinutes", expression = "java(incident.getDurationMinutes())")
    IncidentResponse toResponse(Incident incident);
}

