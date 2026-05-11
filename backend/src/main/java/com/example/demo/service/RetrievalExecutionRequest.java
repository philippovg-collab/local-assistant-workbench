package com.example.demo.service;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record RetrievalExecutionRequest(
    String query,
    KnowledgeScope knowledgeScope,
    RetrievalFilters retrievalFilters,
    List<String> dismissedRetrievalHintKeys,
    Integer finalLimit,
    boolean recordWindow,
    Instant referenceInstant,
    Set<String> materialIds
) {
    public RetrievalExecutionRequest {
        query = query == null ? null : query.trim();
        knowledgeScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        retrievalFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        dismissedRetrievalHintKeys = dismissedRetrievalHintKeys == null
            ? List.of()
            : dismissedRetrievalHintKeys.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        materialIds = materialIds == null
            ? null
            : materialIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static RetrievalExecutionRequest forSearch(
        String query,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> dismissedRetrievalHintKeys,
        int finalLimit,
        boolean recordWindow
    ) {
        return new RetrievalExecutionRequest(
            query,
            knowledgeScope,
            retrievalFilters,
            dismissedRetrievalHintKeys,
            finalLimit,
            recordWindow,
            null,
            null
        );
    }
}
