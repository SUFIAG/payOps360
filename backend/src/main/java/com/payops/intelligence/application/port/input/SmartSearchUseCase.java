package com.payops.intelligence.application.port.input;

import com.payops.shared.application.Actor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Translates natural-language questions ("failed card payments over 500 USD on
 * stripe yesterday") into structured payment filters.
 */
public interface SmartSearchUseCase {

    SearchInterpretation interpret(Actor actor, String query);

    record SearchInterpretation(List<String> statuses, String provider, String method, String risk, String currency,
                                BigDecimal minAmount, BigDecimal maxAmount, String country, Integer lastHours,
                                Boolean stuck, String text, String explanation, String engine) {
    }
}
