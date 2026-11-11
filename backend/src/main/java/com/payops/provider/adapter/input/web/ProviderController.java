package com.payops.provider.adapter.input.web;

import com.payops.provider.application.port.input.ProviderCatalogUseCase;
import com.payops.provider.application.port.input.ProviderHealthUseCase;
import com.payops.provider.application.port.input.ProviderView;
import com.payops.provider.domain.HealthSnapshot;
import com.payops.provider.domain.ProviderType;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.application.RequestContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Tag(name = "Providers")
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
class ProviderController {

    private final ProviderCatalogUseCase catalog;
    private final ProviderHealthUseCase health;

    @GetMapping
    @PreAuthorize("hasAuthority('PROVIDER_READ')")
    ApiResponse<List<ProviderView>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(catalog.list(user.organizationId()));
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('PROVIDER_READ')")
    ApiResponse<ProviderView> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String code) {
        return ApiResponse.ok(catalog.get(user.organizationId(), code));
    }

    @GetMapping("/{code}/health")
    @PreAuthorize("hasAuthority('PROVIDER_READ')")
    ApiResponse<List<HealthSnapshot>> health(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String code,
                                             @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        return ApiResponse.ok(health.history(user.organizationId(), code, Duration.ofHours(Math.min(Math.max(hours, 1), 168))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROVIDER_MANAGE')")
    ApiResponse<ProviderView> register(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody ProviderRequest request, HttpServletRequest http) {
        return ApiResponse.ok(catalog.register(user.actor(), request.toDefinition(), context(http)));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('PROVIDER_MANAGE')")
    ApiResponse<ProviderView> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String code,
                                     @Valid @RequestBody ProviderRequest request, HttpServletRequest http) {
        return ApiResponse.ok(catalog.update(user.actor(), code, request.toDefinition(), context(http)));
    }

    @PostMapping("/{code}/activate")
    @PreAuthorize("hasAuthority('PROVIDER_MANAGE')")
    ApiResponse<ProviderView> activate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String code,
                                       HttpServletRequest http) {
        return ApiResponse.ok(catalog.setActive(user.actor(), code, true, context(http)));
    }

    @PostMapping("/{code}/deactivate")
    @PreAuthorize("hasAuthority('PROVIDER_MANAGE')")
    ApiResponse<ProviderView> deactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String code,
                                         HttpServletRequest http) {
        return ApiResponse.ok(catalog.setActive(user.actor(), code, false, context(http)));
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    record ProviderRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{1,59}$") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull ProviderType type,
            @Size(max = 60) String region,
            @Min(50) @Max(120000) int slaLatencyMs,
            @NotNull @DecimalMin("50") @DecimalMax("100") BigDecimal slaSuccessRate,
            @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal costBps) {

        ProviderCatalogUseCase.ProviderDefinition toDefinition() {
            return new ProviderCatalogUseCase.ProviderDefinition(code, name, type, region, slaLatencyMs, slaSuccessRate, costBps);
        }
    }
}
