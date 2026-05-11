package com.example.demo.model.eval;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateRetrievalEvalRunRequest(
    String datasetId,
    String datasetVersion,
    List<String> caseIds,
    String corpusSnapshotId,
    String executionConfigHash,
    Instant referenceInstant,
    Integer limit,
    @Size(max = 32)
    List<String> tags
) {
    public CreateRetrievalEvalRunRequest {
        caseIds = caseIds == null
            ? List.of()
            : caseIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        tags = tags == null
            ? List.of()
            : tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
