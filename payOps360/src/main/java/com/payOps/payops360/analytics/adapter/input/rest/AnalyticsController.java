package com.payOps.payops360.analytics.adapter.input.rest;

import com.payOps.payops360.analytics.adapter.input.rest.dto.AnalyticsReportResponse;
import com.payOps.payops360.analytics.adapter.input.rest.mapper.AnalyticsRestMapper;
import com.payOps.payops360.analytics.application.port.input.GenerateAnalyticsReportUseCase;
import com.payOps.payops360.analytics.domain.model.AnalyticsReport;
import com.payOps.payops360.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST Controller for Analytics
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Advanced analytics and trend analysis APIs")
public class AnalyticsController {

    private final GenerateAnalyticsReportUseCase generateAnalyticsReportUseCase;
    private final AnalyticsRestMapper mapper;

    @GetMapping("/payment-trends")
    @Operation(summary = "Get payment trends", description = "Generate payment trends analysis report")
    public ResponseEntity<ApiResponse<AnalyticsReportResponse>> getPaymentTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "HOURLY") String dimension
    ) {
        GenerateAnalyticsReportUseCase.AnalyticsQuery query =
                new GenerateAnalyticsReportUseCase.AnalyticsQuery(startTime, endTime, dimension, null);

        AnalyticsReport report = generateAnalyticsReportUseCase.generatePaymentTrends(query);
        AnalyticsReportResponse response = mapper.toResponse(report);

        return ResponseEntity.ok(ApiResponse.success(response, "Payment trends report generated"));
    }

    @GetMapping("/provider-performance")
    @Operation(summary = "Get provider performance", description = "Generate provider performance analysis report")
    public ResponseEntity<ApiResponse<AnalyticsReportResponse>> getProviderPerformance(
            @RequestParam String providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        GenerateAnalyticsReportUseCase.AnalyticsQuery query =
                new GenerateAnalyticsReportUseCase.AnalyticsQuery(startTime, endTime, "HOURLY", providerId);

        AnalyticsReport report = generateAnalyticsReportUseCase.generateProviderPerformance(query);
        AnalyticsReportResponse response = mapper.toResponse(report);

        return ResponseEntity.ok(ApiResponse.success(response, "Provider performance report generated"));
    }
}

