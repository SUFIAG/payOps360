package com.payops.intelligence.application.port.out;

/**
 * Tenant-level AI policy and the usage ledger required for AI governance
 * (who used which feature, through which engine, at what token cost).
 */
public interface AiGovernancePort {

    boolean generativeAiEnabled(long organizationId);

    void recordUsage(long organizationId, Long userId, String feature, String engine, long inputTokens,
                     long outputTokens, long latencyMs, boolean success);
}
