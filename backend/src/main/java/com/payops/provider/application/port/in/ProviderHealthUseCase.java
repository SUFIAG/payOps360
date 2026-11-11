package com.payops.provider.application.port.in;

import com.payops.provider.domain.HealthSnapshot;

import java.time.Duration;
import java.util.List;

public interface ProviderHealthUseCase {

    /** Health history of one provider, oldest first. */
    List<HealthSnapshot> history(long organizationId, String code, Duration lookback);

    /** Recomputes the health of every active provider (scheduled). Returns the number of snapshots written. */
    int captureSnapshots();
}
