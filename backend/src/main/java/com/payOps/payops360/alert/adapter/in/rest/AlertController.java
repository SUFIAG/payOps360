package com.payops.payops360.alert.adapter.in.rest;

import com.payops.payops360.alert.adapter.in.rest.dto.AlertResponse;
import com.payops.payops360.alert.adapter.in.rest.dto.CreateAlertRequest;
import com.payops.payops360.alert.adapter.in.rest.dto.ResolveAlertRequest;
import com.payops.payops360.alert.adapter.in.rest.mapper.AlertRestMapper;
import com.payops.payops360.alert.application.port.in.*;
import com.payops.payops360.alert.domain.model.Alert;
import com.payops.payops360.alert.domain.model.AlertStatus;
import com.payops.payops360.common.dto.ApiResponse;
import com.payops.payops360.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Alert REST Controller - INPUT ADAPTER
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert management APIs")
public class AlertController {
    
    private final CreateAlertUseCase createAlertUseCase;
    private final GetAlertUseCase getAlertUseCase;
    private final ListAlertsUseCase listAlertsUseCase;
    private final UpdateAlertUseCase updateAlertUseCase;
    private final AlertRestMapper mapper;
    
    @PostMapping
    @Operation(summary = "Create Alert", description = "Create a new alert")
    public ResponseEntity<ApiResponse<AlertResponse>> createAlert(
            @Valid @RequestBody CreateAlertRequest request) {
        
        log.info("Creating alert: type={}, entity={}:{}", 
                request.getAlertType(), request.getEntityType(), request.getEntityId());
        
        CreateAlertUseCase.CreateAlertCommand command = mapper.toCommand(request);
        Alert alert = createAlertUseCase.create(command);
        AlertResponse response = mapper.toResponse(alert);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
    
    @GetMapping("/{alertId}")
    @Operation(summary = "Get Alert", description = "Retrieve alert by ID")
    public ResponseEntity<ApiResponse<AlertResponse>> getAlert(@PathVariable String alertId) {
        log.debug("Fetching alert: alertId={}", alertId);
        
        Alert alert = getAlertUseCase.getByAlertId(alertId);
        AlertResponse response = mapper.toResponse(alert);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping
    @Operation(summary = "List Alerts", description = "List alerts with filtering")
    public ResponseEntity<ApiResponse<PagedResponse<AlertResponse>>> listAlerts(
            @RequestParam(required = false) @Parameter(description = "Filter by status") AlertStatus status,
            @RequestParam(required = false) @Parameter(description = "Filter by entity type") String entityType,
            @RequestParam(required = false) @Parameter(description = "Filter by entity ID") String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        
        log.debug("Listing alerts: status={}, entityType={}, entityId={}", status, entityType, entityId);
        
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortBy));
        
        Page<Alert> alerts;
        if (status != null) {
            alerts = listAlertsUseCase.listByStatus(status, pageable);
        } else if (entityType != null && entityId != null) {
            alerts = listAlertsUseCase.listByEntity(entityType, entityId, pageable);
        } else {
            alerts = listAlertsUseCase.listAll(pageable);
        }
        
        Page<AlertResponse> responsePage = alerts.map(mapper::toResponse);
        PagedResponse<AlertResponse> pagedResponse = PagedResponse.from(responsePage);
        
        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }
    
    @PatchMapping("/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge Alert", description = "Acknowledge an alert")
    public ResponseEntity<ApiResponse<AlertResponse>> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestParam String userId) {
        
        log.info("Acknowledging alert: alertId={}, userId={}", alertId, userId);
        
        Alert alert = updateAlertUseCase.acknowledge(alertId, userId);
        AlertResponse response = mapper.toResponse(alert);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PatchMapping("/{alertId}/resolve")
    @Operation(summary = "Resolve Alert", description = "Resolve an alert")
    public ResponseEntity<ApiResponse<AlertResponse>> resolveAlert(
            @PathVariable String alertId,
            @Valid @RequestBody ResolveAlertRequest request) {
        
        log.info("Resolving alert: alertId={}", alertId);
        
        UpdateAlertUseCase.ResolveAlertCommand command = mapper.toResolveCommand(alertId, request);
        Alert alert = updateAlertUseCase.resolve(command);
        AlertResponse response = mapper.toResponse(alert);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
