package com.payops.analytics.adapter.in.web;

import com.payops.analytics.application.port.in.AnalyticsUseCase;
import com.payops.analytics.domain.AnalyticsModels.Breakdown;
import com.payops.analytics.domain.AnalyticsModels.Corridor;
import com.payops.analytics.domain.AnalyticsModels.Overview;
import com.payops.analytics.domain.AnalyticsModels.ProviderPerformance;
import com.payops.analytics.domain.AnalyticsModels.TimeBucket;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Windows are expressed as ISO-8601 durations, e.g. {@code PT24H}, {@code P7D}.
 */
@Tag(name = "Analytics")
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAuthority('ANALYTICS_READ') or hasAuthority('PAYMENT_READ')")
@RequiredArgsConstructor
class AnalyticsController {

    private final AnalyticsUseCase analytics;

    @GetMapping("/overview")
    ApiResponse<Overview> overview(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(defaultValue = "PT24H") Duration window) {
        return ApiResponse.ok(analytics.overview(user.organizationId(), window));
    }

    @GetMapping("/timeseries")
    ApiResponse<List<TimeBucket>> timeseries(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(defaultValue = "PT24H") Duration window,
                                             @RequestParam(defaultValue = "PT1H") Duration bucket) {
        return ApiResponse.ok(analytics.timeseries(user.organizationId(), window, bucket));
    }

    @GetMapping("/providers")
    ApiResponse<List<ProviderPerformance>> providers(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(defaultValue = "PT24H") Duration window) {
        return ApiResponse.ok(analytics.providerPerformance(user.organizationId(), window));
    }

    @GetMapping("/failure-reasons")
    ApiResponse<List<Breakdown>> failureReasons(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam(defaultValue = "PT24H") Duration window) {
        return ApiResponse.ok(analytics.failureReasons(user.organizationId(), window));
    }

    @GetMapping("/methods")
    ApiResponse<List<Breakdown>> methods(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(defaultValue = "PT24H") Duration window) {
        return ApiResponse.ok(analytics.methodMix(user.organizationId(), window));
    }

    @GetMapping("/risk")
    ApiResponse<List<Breakdown>> risk(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(defaultValue = "PT24H") Duration window) {
        return ApiResponse.ok(analytics.riskDistribution(user.organizationId(), window));
    }

    @GetMapping("/corridors")
    ApiResponse<List<Corridor>> corridors(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestParam(defaultValue = "PT24H") Duration window) {
        return ApiResponse.ok(analytics.corridors(user.organizationId(), window));
    }
}
