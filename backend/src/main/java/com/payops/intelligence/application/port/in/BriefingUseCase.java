package com.payops.intelligence.application.port.in;

import com.payops.shared.application.Actor;

import java.time.Instant;

/**
 * Executive operations briefing (markdown) for the last 24 hours.
 */
public interface BriefingUseCase {

    Briefing briefing(Actor actor);

    record Briefing(String markdown, String engine, Instant generatedAt) {
    }
}
