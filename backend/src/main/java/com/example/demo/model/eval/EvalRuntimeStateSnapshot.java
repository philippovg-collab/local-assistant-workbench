package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record EvalRuntimeStateSnapshot(
    Map<String, Object> state,
    String searchStateHash,
    Instant capturedAt
) {
    public EvalRuntimeStateSnapshot {
        state = state == null ? Map.of() : state;
    }
}
