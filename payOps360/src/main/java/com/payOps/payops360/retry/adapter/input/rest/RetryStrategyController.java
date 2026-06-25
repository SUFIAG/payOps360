package com.payOps.payops360.retry.adapter.input.rest;

import com.payOps.payops360.common.dto.ApiResponse;
import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.retry.adapter.input.rest.dto.RetryRecommendationRequest;
import com.payOps.payops360.retry.adapter.input.rest.dto.RetryStrategyResponse;
import com.payOps.payops360.retry.adapter.input.rest.mapper.RetryStrategyRestMapper;
import com.payOps.payops360.retry.application.port.input.*;
import com.payOps.payops360.retry.domain.model.RetryStatus;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Retry Strategy Management
 */
@RestController
@RequestMapping("/api/v1/retries")
@RequiredArgsConstructor
@Tag(name = "Retry Strategy", description = "Intelligent retry recommendation and execution APIs")
public class RetryStrategyController {

    private final RecommendRetryStrategyUseCase recommendRetryStrategyUseCase;
    private final ExecuteRetryUseCase executeRetryUseCase;
    private final GetRetryStrategyUseCase getRetryStrategyUseCase;
    private final QueryRetryStrategiesUseCase queryRetryStrategiesUseCase;
    private final RetryStrategyRestMapper mapper;

    @PostMapping("/recommend")
    @Operation(summary = "Recommend retry strategy", description = "Get intelligent retry recommendation for failed payment")
    public ResponseEntity<ApiResponse<RetryStrategyResponse>> recommend(
            @Valid @RequestBody RetryRecommendationRequest request
    ) {
        RecommendRetryStrategyUseCase.RecommendRetryCommand command =
                new RecommendRetryStrategyUseCase.RecommendRetryCommand(
                        request.paymentId(),
                        request.failureCategory(),
                        request.providerSuccessRate(),
                        request.failureCount()
                );

        RetryStrategy strategy = recommendRetryStrategyUseCase.recommend(command);
        RetryStrategyResponse response = mapper.toResponse(strategy);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Retry strategy recommended successfully"));
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "Execute retry strategy", description = "Execute a recommended retry strategy")
    public ResponseEntity<ApiResponse<RetryStrategyResponse>> execute(@PathVariable UUID id) {
        RetryStrategy strategy = executeRetryUseCase.execute(id);
        RetryStrategyResponse response = mapper.toResponse(strategy);

        return ResponseEntity.ok(ApiResponse.success(response, "Retry strategy execution started"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get retry strategy", description = "Get retry strategy by ID")
    public ResponseEntity<ApiResponse<RetryStrategyResponse>> getById(@PathVariable UUID id) {
        RetryStrategy strategy = getRetryStrategyUseCase.getById(id);
        RetryStrategyResponse response = mapper.toResponse(strategy);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Query retry strategies", description = "Get retry strategies with filters")
    public ResponseEntity<PagedResponse<RetryStrategyResponse>> findAll(
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) RetryStatus status,
            @RequestParam(required = false) String failureCategory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        QueryRetryStrategiesUseCase.RetryQueryParams params =
                new QueryRetryStrategiesUseCase.RetryQueryParams(
                        paymentId, status, failureCategory, page, size
                );

        PagedResponse<RetryStrategy> strategies = queryRetryStrategiesUseCase.findAll(params);
        PagedResponse<RetryStrategyResponse> response = strategies.map(mapper::toResponse);

        return ResponseEntity.ok(response);
    }
}

