package com.payops.payment.application.port.in;

import com.payops.payment.domain.Payment;
import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.PaymentStatus;
import com.payops.payment.domain.RiskAssessment;
import com.payops.payment.application.port.out.ProviderDirectory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentView(
        String reference,
        String merchantReference,
        BigDecimal amount,
        String currency,
        String providerCode,
        String providerName,
        String customerRef,
        PaymentMethod method,
        String cardLast4,
        String country,
        PaymentStatus status,
        PaymentStatus previousStatus,
        Instant statusChangedAt,
        String failureCode,
        String failureMessage,
        Integer latencyMs,
        int retryCount,
        boolean stuck,
        int riskScore,
        RiskAssessment.RiskLevel riskLevel,
        List<String> riskFactors,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentView of(Payment p, ProviderDirectory.ProviderRef provider) {
        return new PaymentView(p.reference(), p.merchantReference(), p.money().amount(), p.money().currency(),
                provider == null ? null : provider.code(), provider == null ? null : provider.name(),
                p.customerRef(), p.method(), p.cardLast4(), p.country(), p.status(), p.previousStatus(),
                p.statusChangedAt(), p.failureCode(), p.failureMessage(), p.latencyMs(), p.retryCount(), p.stuck(),
                p.risk().score(), p.risk().level(), p.risk().factors(), p.createdAt(), p.updatedAt());
    }
}
