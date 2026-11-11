package com.payops.provider.application.port.output;

import com.payops.provider.domain.TrafficWindow;

import java.time.Instant;
import java.util.List;

/**
 * Read model of payment traffic per provider, used to judge health.
 */
public interface TrafficStatisticsPort {

    List<TrafficWindow> trafficByProvider(Instant from, Instant to);
}
