package com.payops.intelligence.application.port.input;

import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.time.Instant;
import java.util.List;

/**
 * "Explain with AI" for reconciliation exceptions: likely cause and the
 * suggested next action. Falls back to a deterministic explanation when
 * generative AI is disabled or unavailable. Advisory only — it never acts.
 */
public interface ReconciliationExplainUseCase {

    ExceptionExplanation explainException(Actor actor, String exceptionReference, RequestContext context);

    record ExceptionExplanation(String reference, String likelyCause, String suggestedAction, List<String> checks,
                                String confidence, String engine, Instant generatedAt) {
    }
}
