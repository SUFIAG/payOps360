package com.payops.payops360.provider.domain.model;

/**
 * Provider types in the payment ecosystem.
 */
public enum ProviderType {

    GATEWAY("Payment Gateway"),
    PROCESSOR("Payment Processor"),
    ACQUIRER("Acquiring Bank"),
    WALLET("Digital Wallet Provider"),
    BANK("Bank/Financial Institution");

    private final String displayName;

    ProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

