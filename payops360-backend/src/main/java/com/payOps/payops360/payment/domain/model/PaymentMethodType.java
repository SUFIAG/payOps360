package com.payops.payops360.payment.domain.model;

/**
 * Payment method types supported by the system.
 */
public enum PaymentMethodType {
    CARD("Credit/Debit Card"),
    BANK_TRANSFER("Bank Transfer"),
    WALLET("Digital Wallet"),
    UPI("Unified Payments Interface"),
    NET_BANKING("Net Banking"),
    CASH("Cash"),
    OTHER("Other");

    private final String displayName;

    PaymentMethodType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

