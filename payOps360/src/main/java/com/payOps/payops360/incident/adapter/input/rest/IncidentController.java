package com.payOps.payops360.incident.adapter.input.rest;

import com.payOps.payops360.common.dto.ApiResponse;
import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.incident.adapter.input.rest.dto.*;
import com.payOps.payops360.incident.adapter.input.rest.mapper.IncidentRestMapper;
import com.payOps/payops360.incident.application.port.input.*;
import com.payOps/payops360.incident.domain.model.Incident;
import com.payOps.payops360.incident.domain.model.IncidentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Incident Management
 */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident Management", description = "Incident correlation and management APIs")
public class IncidentController {

    private final ManageIncidentUseCase manageIncidentUseCase;
    private final QueryIncidentsUseCase queryIncidentsUseCase;
    private final IncidentRestMapper mapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get incident", description = "Get incident by ID")
    public ResponseEntity<ApiResponse<IncidentResponse>> getById(@PathVariable UUID id) {
        Incident incident = queryIncidentsUseCase.getById(id);
        IncidentResponse response = mapper.toResponse(incident);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Query incidents", description = "Get incidents with filters")
    public ResponseEntity<PagedResponse<IncidentResponse>> findAll(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        QueryIncidentsUseCase.IncidentQueryParams params =
                new QueryIncidentsUseCase.IncidentQueryParams(status, providerId, severity, page, size);

        PagedResponse<Incident> incidents = queryIncidentsUseCase.findAll(params);
        PagedResponse<IncidentResponse> response = incidents.map(mapper::toResponse);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge incident", description = "Acknowledge incident and assign to team member")
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledge(
            @PathVariable UUID id,
            @Valid @RequestBody AcknowledgeIncidentRequest request
    ) {
        Incident incident = manageIncidentUseCase.acknowledge(id, request.assignedTo());
        IncidentResponse response = mapper.toResponse(incident);
        return ResponseEntity.ok(ApiResponse.success(response, "Incident acknowledged"));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve incident", description = "Resolve incident with root cause and resolution")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveIncidentRequest request
    ) {
        Incident incident = manageIncidentUseCase.resolve(id, request.rootCause(), request.resolution());
        IncidentResponse response = mapper.toResponse(incident);
        return ResponseEntity.ok(ApiResponse.success(response, "Incident resolved"));
    }

    @PatchMapping("/{id}/escalate")
    @Operation(summary = "Escalate incident", description = "Escalate incident severity")
    public ResponseEntity<ApiResponse<IncidentResponse>> escalate(@PathVariable UUID id) {
        Incident incident = manageIncidentUseCase.escalate(id);
        IncidentResponse response = mapper.toResponse(incident);
        return ResponseEntity.ok(ApiResponse.success(response, "Incident escalated to " + incident.getSeverity()));
    }
}

