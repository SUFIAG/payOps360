package com.payops.incident.domain;

import java.util.List;

/**
 * Generated postmortem narrative (AI or deterministic), applied to a draft
 * postmortem by a human-triggered action. Always advisory.
 */
public record PostmortemDraft(String summary, String impact, String rootCause, List<Item> actionItems, String engine) {

    public record Item(String text, String owner) {
    }

    public PostmortemDraft {
        actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
    }
}
