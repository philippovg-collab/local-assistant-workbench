package com.example.demo.model.eval;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record RetrievalEvalPreviewRequest(
    @Size(max = 20000)
    String query,
    String datasetId,
    String datasetVersion,
    String caseId,
    Integer caseRevision,
    String corpusSnapshotId,
    String executionConfigHash,
    @Valid
    KnowledgeScope knowledgeScope,
    @Valid
    RetrievalFilters retrievalFilters,
    List<String> dismissedRetrievalHintKeys,
    Instant referenceInstant,
    Integer limit,
    Boolean includeCandidates,
    Boolean includeNeighborChunks
) {
    public RetrievalEvalPreviewRequest {
        query = query == null ? null : query.trim();
        knowledgeScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        retrievalFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        dismissedRetrievalHintKeys = dismissedRetrievalHintKeys == null
            ? List.of()
            : dismissedRetrievalHintKeys.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    public boolean includeCandidatesOrDefault() {
        return includeCandidates == null || includeCandidates;
    }

    public boolean includeNeighborChunksOrDefault() {
        return Boolean.TRUE.equals(includeNeighborChunks);
    }
}
