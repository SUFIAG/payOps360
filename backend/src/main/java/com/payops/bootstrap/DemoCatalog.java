package com.payops.bootstrap;

import com.payops.payment.domain.PaymentMethod;
import com.payops.provider.domain.ProviderType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reference data for the demo tenant: a cross-border payments / remittance
 * operator using acquirers, real-time networks, bank rails and wallets.
 */
final class DemoCatalog {

    record DemoProvider(String code, String name, ProviderType type, String region, int slaLatencyMs,
                        BigDecimal slaSuccessRate, BigDecimal costBps, double baseSuccess, int typicalLatencyMs,
                        int weight, PaymentMethod method, List<String> failureCodes) {
    }

    record Corridor(String country, String currency, double minAmount, double maxAmount, int weight) {
    }

    static final List<DemoProvider> PROVIDERS = List.of(
            new DemoProvider("adyen", "Adyen", ProviderType.CARD_ACQUIRER, "EU", 1200, bd("97.0"), bd("28"),
                    0.975, 420, 20, PaymentMethod.CARD,
                    List.of("INSUFFICIENT_FUNDS", "DO_NOT_HONOR", "CARD_EXPIRED", "AUTHENTICATION_FAILED", "ISSUER_UNAVAILABLE")),
            new DemoProvider("checkout", "Checkout.com", ProviderType.CARD_ACQUIRER, "MENA", 1200, bd("96.5"), bd("25"),
                    0.968, 460, 18, PaymentMethod.CARD,
                    List.of("INSUFFICIENT_FUNDS", "DO_NOT_HONOR", "PROCESSOR_TIMEOUT", "FRAUD_SUSPECTED", "LIMIT_EXCEEDED")),
            new DemoProvider("stripe", "Stripe", ProviderType.PAYMENT_GATEWAY, "Global", 1000, bd("97.5"), bd("35"),
                    0.979, 380, 14, PaymentMethod.CARD,
                    List.of("INSUFFICIENT_FUNDS", "CARD_EXPIRED", "DO_NOT_HONOR", "AUTHENTICATION_FAILED")),
            new DemoProvider("visa-direct", "Visa Direct", ProviderType.REAL_TIME_PAYMENTS, "Global", 1500, bd("98.0"), bd("18"),
                    0.985, 650, 16, PaymentMethod.REAL_TIME_PAYMENT,
                    List.of("INVALID_ACCOUNT", "LIMIT_EXCEEDED", "NETWORK_ERROR", "COMPLIANCE_HOLD")),
            new DemoProvider("swift-gpi", "SWIFT gpi", ProviderType.BANK_TRANSFER, "Global", 4000, bd("99.0"), bd("12"),
                    0.991, 2100, 10, PaymentMethod.BANK_TRANSFER,
                    List.of("INVALID_ACCOUNT", "BENEFICIARY_BANK_REJECTED", "COMPLIANCE_HOLD")),
            new DemoProvider("mpesa", "M-Pesa Corridor", ProviderType.REMITTANCE_CORRIDOR, "Africa", 2500, bd("95.0"), bd("40"),
                    0.955, 1300, 8, PaymentMethod.DIGITAL_WALLET,
                    List.of("INVALID_ACCOUNT", "RATE_LIMITED", "NETWORK_ERROR", "LIMIT_EXCEEDED")),
            new DemoProvider("gcash", "GCash Wallet", ProviderType.DIGITAL_WALLET, "APAC", 1800, bd("96.0"), bd("30"),
                    0.963, 900, 9, PaymentMethod.DIGITAL_WALLET,
                    List.of("INVALID_ACCOUNT", "LIMIT_EXCEEDED", "ISSUER_UNAVAILABLE")),
            new DemoProvider("cash-network", "Cash Pickup Network", ProviderType.REMITTANCE_CORRIDOR, "Global", 3000, bd("97.0"), bd("55"),
                    0.972, 1600, 5, PaymentMethod.CASH_PICKUP,
                    List.of("COMPLIANCE_HOLD", "INVALID_REQUEST", "NETWORK_ERROR")));

    static final List<Corridor> CORRIDORS = List.of(
            new Corridor("PK", "GBP", 40, 1200, 16),
            new Corridor("IN", "GBP", 50, 2500, 14),
            new Corridor("PH", "EUR", 30, 900, 12),
            new Corridor("BD", "GBP", 40, 1000, 8),
            new Corridor("EG", "EUR", 50, 1500, 6),
            new Corridor("NG", "GBP", 60, 2000, 6),
            new Corridor("MX", "USD", 50, 1800, 10),
            new Corridor("KE", "USD", 30, 800, 6),
            new Corridor("PK", "SAR", 300, 6000, 9),
            new Corridor("IN", "AED", 300, 9000, 9),
            new Corridor("ES", "EUR", 80, 4000, 4));

    /** Provider that periodically degrades in the live simulation (demonstrates AI detection). */
    static final String UNSTABLE_PROVIDER = "checkout";

    private DemoCatalog() {
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
