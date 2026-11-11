package com.payops.provider.application.port.output;

import com.payops.provider.domain.HealthSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface HealthSnapshotRepository {

    void saveAll(List<HealthSnapshot> snapshots);

    /** Latest snapshot per provider id. */
    Map<Long, HealthSnapshot> latestFor(List<Long> providerIds);

    List<HealthSnapshot> history(long providerId, Instant since);

    int deleteOlderThan(Instant cutoff);
}
