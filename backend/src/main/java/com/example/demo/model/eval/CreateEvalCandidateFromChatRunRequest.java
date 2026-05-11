package com.example.demo.model.eval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateEvalCandidateFromChatRunRequest(
    @NotBlank
    @Size(max = 36)
    String chatRunId,
    @Size(max = 36)
    String datasetId,
    @Size(max = 128)
    String caseKey,
    @Size(max = 10_000)
    String question,
    @Size(max = 2_000)
    String failureReason,
    Map<String, Object> artifacts,
    List<Map<String, Object>> originalSources,
    @Size(max = 64)
    List<@Size(max = 64) String> tags
) {
    public CreateEvalCandidateFromChatRunRequest {
        artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
        originalSources = originalSources == null ? List.of() : List.copyOf(originalSources);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
