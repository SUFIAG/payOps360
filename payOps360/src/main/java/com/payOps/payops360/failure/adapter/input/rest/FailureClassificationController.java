package com.payOps/payops360.failure.adapter.input.rest;

import com.payOps/payops360.common.dto.ApiResponse;
import com.payOps/payops360.common.dto.PagedResponse;
import com.payOps/payops360.failure.adapter.input.rest.dto.ClassifyFailureRequest;
import com.payOps/payops360.failure.adapter.input.rest.dto.FailureClassificationResponse;
import com.payOps.payops360.failure.adapter.input.rest.mapper.FailureClassificationRestMapper;
import com.payOps/payops360.failure.application.port.input.*;
import com.payOps/payops360.failure.domain.model.FailureClassification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Failure Classification
 */
@RestController
@RequestMapping("/api/v1/failures")
@RequiredArgsConstructor
@Tag(name = "Failure Classification", description = "Intelligent failure classification APIs")
public class FailureClassificationController {

    private final ClassifyFailureUseCase classifyFailureUseCase;
    private final QueryFailureClassificationsUseCase queryFailureClassificationsUseCase;
    private final FailureClassificationRestMapper mapper;

    @PostMapping("/classify")
    @Operation(summary = "Classify failure", description = "Classify payment failure with intelligent categorization")
    public ResponseEntity<ApiResponse<FailureClassificationResponse>> classify(
            @Valid @RequestBody ClassifyFailureRequest request
    ) {
        ClassifyFailureUseCase.ClassifyFailureCommand command =
                new ClassifyFailureUseCase.ClassifyFailureCommand(
                        request.paymentId(),
                        request.errorCode(),
                        request.errorMessage(),
                        request.providerId()
                );

        FailureClassification classification = classifyFailureUseCase.classify(command);
        FailureClassificationResponse response = mapper.toResponse(classification);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Failure classified successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get failure classification", description = "Get failure classification by ID")
    public ResponseEntity<ApiResponse<FailureClassificationResponse>> getById(@PathVariable UUID id) {
        FailureClassification classification = queryFailureClassificationsUseCase.getById(id);
        FailureClassificationResponse response = mapper.toResponse(classification);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Query failure classifications", description = "Get failure classifications with filters")
    public ResponseEntity<PagedResponse<FailureClassificationResponse>> findAll(
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) String failureType,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        QueryFailureClassificationsUseCase.FailureQueryParams params =
                new QueryFailureClassificationsUseCase.FailureQueryParams(
                        paymentId, failureType, category, page, size
                );

        PagedResponse<FailureClassification> classifications = queryFailureClassificationsUseCase.findAll(params);
        PagedResponse<FailureClassificationResponse> response = classifications.map(mapper::toResponse);

        return ResponseEntity.ok(response);
    }
}

