package com.payops.intelligence.application.port.out;

import com.payops.intelligence.application.port.in.InsightsUseCase.Anomaly;

/**
 * Raises alerts for anomalies detected by the intelligence module.
 */
public interface AlertingPort {

    void raiseAnomaly(long organizationId, long providerId, Anomaly anomaly);
}
