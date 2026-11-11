package com.payops.reconciliation.application.port.in;

import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Expected provider fee schedules used for fee-leakage detection.
 */
public interface FeeScheduleUseCase {

    FeeScheduleView feeSchedule(long organizationId, String providerCode);

    FeeScheduleView replaceFeeSchedule(Actor actor, String providerCode, List<FeeRuleView> rules,
                                       RequestContext context);

    /** {@code method}/{@code currency} null means "any". Fixed fee is in the settlement currency. */
    record FeeRuleView(String method, String currency, BigDecimal percentage, BigDecimal fixedFee) {
    }

    /**
     * @param fallbackPercentage percentage applied when no rule matches (from the provider's catalogue cost)
     */
    record FeeScheduleView(String providerCode, String providerName, BigDecimal fallbackPercentage,
                           List<FeeRuleView> rules, String updatedBy, Instant updatedAt) {
    }
}
