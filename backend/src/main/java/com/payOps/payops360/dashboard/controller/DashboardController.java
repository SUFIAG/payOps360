package com.payops.payops360.dashboard.controller;

import com.payops.payops360.common.dto.ApiResponse;
import com.payops.payops360.dashboard.dto.DashboardOverviewResponse;
import com.payops.payops360.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard REST Controller
 *
 * Provides aggregated metrics and KPIs for dashboard display.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard metrics and KPIs")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Get dashboard overview
     */
    @GetMapping("/overview")
    @Operation(summary = "Get Dashboard Overview", description = "Retrieve key metrics for dashboard")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        log.debug("Fetching dashboard overview");

        DashboardOverviewResponse overview = dashboardService.getOverview();
        return ResponseEntity.ok(ApiResponse.success(overview));
    }
}

