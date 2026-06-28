package com.payops.payops360.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dashboard overview metrics DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewResponse {

    // Payment Metrics
    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private long pendingPayments;
    private double successRate;

    // Financial Metrics
    private BigDecimal totalVolume;
    private BigDecimal successfulVolume;
    private BigDecimal failedVolume;

    // Provider Metrics
    private long totalProviders;
    private long activeProviders;
    private long healthyProviders;
    private long degradedProviders;
    private long criticalProviders;
}

