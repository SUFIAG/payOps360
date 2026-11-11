package com.payops.intelligence.application.port.in;

import com.payops.shared.application.Actor;

import java.time.Instant;
import java.util.List;

/**
 * Conversational operations copilot grounded in live, tenant-scoped data.
 */
public interface CopilotUseCase {

    CopilotReply ask(Actor actor, String question, List<Turn> history);

    record Turn(String role, String content) {
    }

    record CopilotReply(String answer, List<String> toolsUsed, List<String> suggestions, String engine,
                        Instant generatedAt) {
    }
}
