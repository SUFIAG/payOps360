package com.payops.payment.adapter.output.alerting;

import com.payops.alert.application.port.input.RaiseAlertUseCase;
import com.payops.alert.domain.AlertSource;
import com.payops.alert.domain.AlertType;
import com.payops.payment.application.port.input.PaymentView;
import com.payops.payment.application.port.output.PaymentSignalPort;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
class PaymentSignalAdapter implements PaymentSignalPort {

    private final RaiseAlertUseCase alerts;

    @Override
    public void highRiskPayment(long organizationId, long providerId, PaymentView payment) {
        alerts.raise(new RaiseAlertUseCase.RaiseAlertCommand(organizationId, AlertType.HIGH_RISK_PAYMENT, Severity.HIGH,
                AlertSource.AI,
                "High-risk payment " + payment.reference(),
                "Risk score " + payment.riskScore() + "/100 for " + payment.amount() + " " + payment.currency()
                        + ". Factors: " + String.join("; ", payment.riskFactors()),
                null, payment.reference(), BigDecimal.valueOf(payment.riskScore()), BigDecimal.valueOf(80),
                "high-risk:" + payment.reference()));
    }

    @Override
    public void stuckPayments(long organizationId, int count) {
        alerts.raise(new RaiseAlertUseCase.RaiseAlertCommand(organizationId, AlertType.STUCK_PAYMENTS,
                count >= 20 ? Severity.HIGH : Severity.MEDIUM, AlertSource.RULE,
                "Payments stuck in processing",
                count + " payment(s) exceeded their processing time budget and need attention",
                null, null, BigDecimal.valueOf(count), BigDecimal.ONE, "stuck-payments"));
    }
}
