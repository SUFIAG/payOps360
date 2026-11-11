package com.payops.intelligence.domain.failure;

import java.util.Locale;
import java.util.Map;

/**
 * Curated knowledge of payment failure codes: what they mean, whether a
 * retry can help, what operations should do and what to tell the customer.
 */
public final class FailureKnowledgeBase {

    public record FailureInsight(String code, FailureCategory category, boolean retryable, String meaning,
                                 String operatorAction, String customerMessage) {
    }

    private static final Map<String, FailureInsight> KNOWLEDGE = Map.ofEntries(
            entry("INSUFFICIENT_FUNDS", FailureCategory.SOFT_DECLINE, true,
                    "The payer's account did not have enough funds.",
                    "Retry after the customer's typical pay cycle or offer a smaller amount / alternative method.",
                    "Your payment could not be completed due to insufficient funds."),
            entry("DO_NOT_HONOR", FailureCategory.SOFT_DECLINE, true,
                    "Generic issuer decline without a specific reason.",
                    "Retry once after 1 hour; if repeated, ask the customer to contact their bank.",
                    "Your bank declined the payment. Please contact your bank or try another method."),
            entry("LIMIT_EXCEEDED", FailureCategory.SOFT_DECLINE, true,
                    "A daily or per-transaction limit was exceeded.",
                    "Retry after the limit window resets or split the payment within limits.",
                    "This payment exceeds a limit on your account."),
            entry("CARD_EXPIRED", FailureCategory.HARD_DECLINE, false,
                    "The card is past its expiry date.",
                    "Do not retry. Request updated card details (enable account-updater if available).",
                    "Your card has expired. Please update your payment details."),
            entry("INVALID_ACCOUNT", FailureCategory.HARD_DECLINE, false,
                    "Beneficiary account number or IBAN is invalid or closed.",
                    "Do not retry. Ask the sender to verify the beneficiary details.",
                    "The recipient account details appear to be incorrect."),
            entry("BENEFICIARY_BANK_REJECTED", FailureCategory.HARD_DECLINE, false,
                    "The receiving bank rejected the credit.",
                    "Contact the corridor partner for the rejection reason; refund if unresolved in SLA.",
                    "The receiving bank could not accept this transfer."),
            entry("ISSUER_UNAVAILABLE", FailureCategory.TECHNICAL, true,
                    "The issuing bank or its processor was unreachable.",
                    "Retry with exponential back-off or route to an alternative acquirer.",
                    "We could not reach your bank. We'll retry automatically."),
            entry("PROCESSOR_TIMEOUT", FailureCategory.TECHNICAL, true,
                    "The provider did not answer within the timeout.",
                    "Check provider health; reconcile before retrying to avoid duplicates, then reroute.",
                    "Your payment is taking longer than usual. We're checking its status."),
            entry("NETWORK_ERROR", FailureCategory.TECHNICAL, true,
                    "Transport-level error between PayOps360 and the provider.",
                    "Retry idempotently; investigate connectivity if the rate is elevated.",
                    "A temporary connection issue occurred. We'll retry automatically."),
            entry("RATE_LIMITED", FailureCategory.TECHNICAL, true,
                    "The provider throttled the request.",
                    "Back off and spread traffic across providers; review contracted TPS.",
                    "The service is busy. We'll retry shortly."),
            entry("FRAUD_SUSPECTED", FailureCategory.RISK, false,
                    "Provider or issuer fraud controls blocked the payment.",
                    "Do not retry. Route to manual review and verify customer identity.",
                    "We need to verify this payment. Our team will contact you."),
            entry("AUTHENTICATION_FAILED", FailureCategory.RISK, true,
                    "Strong customer authentication (3-D Secure / OTP) failed.",
                    "Allow the customer to re-authenticate; repeated failures indicate account takeover risk.",
                    "Authentication failed. Please try again."),
            entry("COMPLIANCE_HOLD", FailureCategory.COMPLIANCE, false,
                    "Sanctions / AML screening placed the payment on hold.",
                    "Escalate to compliance. Never disclose screening details to the customer.",
                    "Your payment is under review. We'll update you shortly."),
            entry("CURRENCY_NOT_SUPPORTED", FailureCategory.VALIDATION, false,
                    "The provider does not support this currency or corridor.",
                    "Fix routing rules so this currency is sent to a capable provider.",
                    "This currency is not supported for the selected method."),
            entry("INVALID_REQUEST", FailureCategory.VALIDATION, false,
                    "The provider rejected the request format or data.",
                    "Inspect integration logs and the provider's error detail; likely a deployment regression.",
                    "Something went wrong with this payment. Please try again later."));

    private FailureKnowledgeBase() {
    }

    public static FailureInsight lookup(String code) {
        if (code == null || code.isBlank()) {
            return unknown("UNSPECIFIED");
        }
        FailureInsight insight = KNOWLEDGE.get(code.toUpperCase(Locale.ROOT));
        return insight != null ? insight : unknown(code);
    }

    public static Map<String, FailureInsight> all() {
        return KNOWLEDGE;
    }

    private static FailureInsight unknown(String code) {
        return new FailureInsight(code, FailureCategory.UNKNOWN, true,
                "Unrecognised failure code from the provider.",
                "Check the provider's documentation and map this code in the failure knowledge base.",
                "Your payment could not be completed. Please try again.");
    }

    private static Map.Entry<String, FailureInsight> entry(String code, FailureCategory category, boolean retryable,
                                                           String meaning, String action, String customerMessage) {
        return Map.entry(code, new FailureInsight(code, category, retryable, meaning, action, customerMessage));
    }
}
