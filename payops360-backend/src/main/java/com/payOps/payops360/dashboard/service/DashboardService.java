package com.payops.payops360.dashboard.service;

import com.payops.payops360.dashboard.dto.DashboardOverviewResponse;
import com.payops.payops360.payment.adapter.out.persistence.repository.PaymentJpaRepository;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import com.payops.payops360.provider.adapter.out.persistence.repository.ProviderHealthJpaRepository;
import com.payops.payops360.provider.adapter.out.persistence.repository.ProviderJpaRepository;
import com.payops.payops360.provider.domain.model.HealthStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Dashboard Service
 *
 * Provides aggregated metrics for dashboard display.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final PaymentJpaRepository paymentRepository;
    private final ProviderJpaRepository providerRepository;
    private final ProviderHealthJpaRepository healthRepository;

    /**
     * Get dashboard overview metrics
     */
    public DashboardOverviewResponse getOverview() {
        log.debug("Calculating dashboard overview metrics");

        // Payment metrics
        long totalPayments = paymentRepository.count();
        long successfulPayments = paymentRepository.countByStatus(PaymentStatus.SETTLED);
        long failedPayments = paymentRepository.countByStatus(PaymentStatus.FAILED) +
                              paymentRepository.countByStatus(PaymentStatus.RETRY_FAILED);
        long pendingPayments = totalPayments - successfulPayments - failedPayments;

        double successRate = totalPayments > 0 ?
                (successfulPayments * 100.0 / totalPayments) : 0.0;

        // Provider metrics
        long totalProviders = providerRepository.count();
        long activeProviders = providerRepository.findByIsActive(true, org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();

        // Count providers by health status (simplified for Phase 1)
        long healthyProviders = (long) (activeProviders * 0.8);  // Placeholder
        long degradedProviders = (long) (activeProviders * 0.15);  // Placeholder
        long criticalProviders = activeProviders - healthyProviders - degradedProviders;

        return DashboardOverviewResponse.builder()
                .totalPayments(totalPayments)
                .successfulPayments(successfulPayments)
                .failedPayments(failedPayments)
                .pendingPayments(pendingPayments)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .totalVolume(BigDecimal.ZERO)  // Will be calculated from payment amounts
                .successfulVolume(BigDecimal.ZERO)
                .failedVolume(BigDecimal.ZERO)
                .totalProviders(totalProviders)
                .activeProviders(activeProviders)
                .healthyProviders(healthyProviders)
                .degradedProviders(degradedProviders)
                .criticalProviders(criticalProviders)
                .build();
    }
}

