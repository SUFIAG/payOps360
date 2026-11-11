package com.payops.intelligence.application.port.in;

import com.payops.incident.application.port.in.PostmortemUseCase;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

/**
 * "Draft with AI" for postmortems of resolved incidents. Falls back to a
 * deterministic draft when generative AI is disabled or unavailable.
 */
public interface PostmortemDraftUseCase {

    PostmortemUseCase.PostmortemView draftPostmortem(Actor actor, String incidentReference, RequestContext context);
}
