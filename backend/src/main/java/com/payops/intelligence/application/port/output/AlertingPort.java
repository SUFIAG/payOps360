package com.payops.intelligence.application.port.output;

import com.payops.intelligence.application.port.input.InsightsUseCase.Anomaly;

/**
 * Raises alerts for anomalies detected by the intelligence module.
 */
public interface AlertingPort {

    void raiseAnomaly(long organizationId, long providerId, Anomaly anomaly);
}
