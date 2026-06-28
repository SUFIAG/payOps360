package com.payops.payops360.alert.adapter.in.rest.mapper;

import com.payops.payops360.alert.adapter.in.rest.dto.AlertResponse;
import com.payops.payops360.alert.adapter.in.rest.dto.CreateAlertRequest;
import com.payops.payops360.alert.adapter.in.rest.dto.ResolveAlertRequest;
import com.payops.payops360.alert.application.port.in.CreateAlertUseCase;
import com.payops.payops360.alert.application.port.in.UpdateAlertUseCase;
import com.payops.payops360.alert.domain.model.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Alert REST DTOs.
 */
@Mapper(componentModel = "spring")
public interface AlertRestMapper {
    
    CreateAlertUseCase.CreateAlertCommand toCommand(CreateAlertRequest request);
    
    AlertResponse toResponse(Alert alert);
    
    @Mapping(target = "alertId", source = "alertId")
    @Mapping(target = "userId", source = "request.userId")
    @Mapping(target = "resolutionNote", source = "request.resolutionNote")
    UpdateAlertUseCase.ResolveAlertCommand toResolveCommand(String alertId, ResolveAlertRequest request);
}
