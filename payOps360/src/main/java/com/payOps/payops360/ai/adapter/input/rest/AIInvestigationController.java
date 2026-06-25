package com.payOps/payops360.ai.adapter.input.rest;

import com.payOps/payops360.ai.adapter.input.rest.dto.AIInvestigationResponse;
import com.payOps/payops360.ai.adapter.input.rest.dto.InvestigateIncidentRequest;
import com.payOps/payops360.ai.adapter.input.rest.mapper.AIRestMapper;
import com.payOps/payops360.ai.application.port.input.*;
import com.payOps/payops360.ai.domain.model.AIInvestigation;
import com.payOps/payops360.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for AI-Powered Investigation
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Investigation", description = "AI-powered root cause analysis and recommendations")
public class AIInvestigationController {

    private final InvestigateIncidentUseCase investigateIncidentUseCase;
    private final QueryAIInvestigationsUseCase queryAIInvestigationsUseCase;
    private final AIRestMapper mapper;

    @PostMapping("/investigate")
    @Operation(summary = "Investigate incident with AI", description = "Generate AI-powered root cause analysis and recommendations")
    public ResponseEntity<ApiResponse<AIInvestigationResponse>> investigate(
            @Valid @RequestBody InvestigateIncidentRequest request
    ) {
        InvestigateIncidentUseCase.InvestigateCommand command =
                new InvestigateIncidentUseCase.InvestigateCommand(request.incidentId(), request.model());

        AIInvestigation investigation = investigateIncidentUseCase.investigate(command);
        AIInvestigationResponse response = mapper.toResponse(investigation);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "AI investigation completed"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get AI investigation", description = "Get AI investigation by ID")
    public ResponseEntity<ApiResponse<AIInvestigationResponse>> getById(@PathVariable UUID id) {
        AIInvestigation investigation = queryAIInvestigationsUseCase.getById(id);
        AIInvestigationResponse response = mapper.toResponse(investigation);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/incident/{incidentId}")
    @Operation(summary = "Get AI investigation by incident", description = "Get AI investigation for a specific incident")
    public ResponseEntity<ApiResponse<AIInvestigationResponse>> getByIncidentId(@PathVariable UUID incidentId) {
        AIInvestigation investigation = queryAIInvestigationsUseCase.getByIncidentId(incidentId);
        AIInvestigationResponse response = mapper.toResponse(investigation);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

