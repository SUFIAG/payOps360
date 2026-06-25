package com.payOps/payops360.system.adapter.input.rest;

import com.payOps/payops360.common.dto.ApiResponse;
import com.payOps/payops360.system.application.service.SystemHealthService;
import com.payOps/payops360.system.domain.model.SystemHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for System Monitoring
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "System health and monitoring APIs")
public class SystemController {

    private final SystemHealthService systemHealthService;

    @GetMapping("/health")
    @Operation(summary = "Get system health", description = "Get comprehensive system health status")
    public ResponseEntity<ApiResponse<SystemHealth>> getHealth() {
        SystemHealth health = systemHealthService.getSystemHealth();

        if (health.isHealthy()) {
            return ResponseEntity.ok(ApiResponse.success(health, "System is healthy"));
        } else if (health.isDegraded()) {
            return ResponseEntity.status(200)
                    .body(ApiResponse.success(health, "System is degraded"));
        } else {
            return ResponseEntity.status(503)
                    .body(ApiResponse.error("SERVICE_UNAVAILABLE", "System is down"));
        }
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get system metrics", description = "Get real-time system metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        SystemHealth health = systemHealthService.getSystemHealth();
        return ResponseEntity.ok(ApiResponse.success(health.getMetrics(), "System metrics retrieved"));
    }
}

