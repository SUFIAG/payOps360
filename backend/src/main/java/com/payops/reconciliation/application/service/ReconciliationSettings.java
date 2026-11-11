package com.payops.reconciliation.application.service;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Matching tolerances and thresholds.
 *
 * @param amountTolerance         max absolute difference for an amount to match
 * @param fuzzyWindow             a line settled at T may fuzzy-match payments created in [T - window, T]
 * @param missingAfter            age after which an unsettled succeeded payment is flagged
 * @param feeTolerance            fee excess tolerated before FEE_OVERCHARGE
 * @param exceptionAlertThreshold exceptions per batch that trigger a RECONCILIATION_EXCEPTIONS event
 * @param maxLinesPerFile         hard cap on rows per settlement file
 */
public record ReconciliationSettings(BigDecimal amountTolerance, Duration fuzzyWindow, Duration missingAfter,
                                     BigDecimal feeTolerance, int exceptionAlertThreshold, int maxLinesPerFile) {
}
