package com.example.demo.service.context;

import com.example.demo.model.ConversationSummarySourceRef;
import java.util.List;

public record ConversationSummaryPayload(
    String summaryText,
    List<String> facts,
    List<String> activeEntities,
    List<ConversationSummarySourceRef> sourceRefs,
    List<Integer> coveredTurnNos
) {
    public ConversationSummaryPayload {
        facts = facts == null ? List.of() : List.copyOf(facts);
        activeEntities = activeEntities == null ? List.of() : List.copyOf(activeEntities);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        coveredTurnNos = coveredTurnNos == null ? List.of() : List.copyOf(coveredTurnNos);
    }
}
