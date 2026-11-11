package com.payops.intelligence.application.port.input;

public interface AnomalyScanUseCase {

    /** Scans every tenant's provider metrics and raises AI alerts. Returns the number of anomalies found. */
    int scan();
}
