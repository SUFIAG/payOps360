package com.payops.payops360.provider.adapter.in.rest;

import com.payops.payops360.common.dto.ApiResponse;
import com.payops.payops360.common.dto.PagedResponse;
import com.payops.payops360.provider.adapter.in.rest.dto.ProviderHealthResponse;
import com.payops.payops360.provider.adapter.in.rest.dto.ProviderResponse;
import com.payops.payops360.provider.adapter.in.rest.dto.RegisterProviderRequest;
import com.payops.payops360.provider.adapter.in.rest.mapper.ProviderRestMapper;
import com.payops.payops360.provider.application.port.in.GetProviderUseCase;
import com.payops.payops360.provider.application.port.in.ListProvidersUseCase;
import com.payops.payops360.provider.application.port.in.RegisterProviderUseCase;
import com.payops.payops360.provider.domain.model.Provider;
import com.payops.payops360.provider.domain.model.ProviderHealth;
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
 * Provider REST Controller - INPUT ADAPTER
 *
 * Handles HTTP requests for provider management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@Tag(name = "Providers", description = "Payment provider management APIs")
public class ProviderController {

    private final RegisterProviderUseCase registerProviderUseCase;
    private final GetProviderUseCase getProviderUseCase;
    private final ListProvidersUseCase listProvidersUseCase;
    private final ProviderRestMapper mapper;

    /**
     * Register a new provider
     */
    @PostMapping
    @Operation(summary = "Register Provider", description = "Register a new payment provider")
    public ResponseEntity<ApiResponse<ProviderResponse>> registerProvider(
            @Valid @RequestBody RegisterProviderRequest request) {

        log.info("Registering provider: providerId={}", request.getProviderId());

        RegisterProviderUseCase.RegisterProviderCommand command = mapper.toCommand(request);
        Provider provider = registerProviderUseCase.register(command);
        ProviderResponse response = mapper.toResponse(provider);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Get provider by ID
     */
    @GetMapping("/{providerId}")
    @Operation(summary = "Get Provider", description = "Retrieve provider by ID")
    public ResponseEntity<ApiResponse<ProviderResponse>> getProvider(
            @PathVariable String providerId) {

        log.debug("Fetching provider: providerId={}", providerId);

        Provider provider = getProviderUseCase.getByProviderId(providerId);
        ProviderResponse response = mapper.toResponse(provider);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get provider health
     */
    @GetMapping("/{providerId}/health")
    @Operation(summary = "Get Provider Health", description = "Retrieve current health metrics")
    public ResponseEntity<ApiResponse<ProviderHealthResponse>> getProviderHealth(
            @PathVariable String providerId) {

        log.debug("Fetching provider health: providerId={}", providerId);

        ProviderHealth health = getProviderUseCase.getHealth(providerId);
        ProviderHealthResponse response = mapper.toHealthResponse(health);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * List all providers
     */
    @GetMapping
    @Operation(summary = "List Providers", description = "List all providers with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<ProviderResponse>>> listProviders(
            @RequestParam(required = false) @Parameter(description = "Filter by active status") Boolean active,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size") int size,
            @RequestParam(defaultValue = "providerName") @Parameter(description = "Sort field") String sortBy,
            @RequestParam(defaultValue = "ASC") @Parameter(description = "Sort direction") Sort.Direction direction) {

        log.debug("Listing providers: active={}", active);

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortBy));

        Page<Provider> providers = (active != null && active) ?
                listProvidersUseCase.listActive(pageable) :
                listProvidersUseCase.listAll(pageable);

        Page<ProviderResponse> responsePage = providers.map(mapper::toResponse);
        PagedResponse<ProviderResponse> pagedResponse = PagedResponse.from(responsePage);

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }
}

