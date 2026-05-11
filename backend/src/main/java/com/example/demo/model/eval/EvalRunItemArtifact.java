package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record EvalRunItemArtifact(
    String runItemId,
    EvalRunItemArtifactType artifactType,
    Map<String, Object> payload,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalRunItemArtifact {
        payload = payload == null ? Map.of() : payload;
    }
}
