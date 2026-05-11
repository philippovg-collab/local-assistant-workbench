package com.example.demo.model.eval;

import java.util.Map;

public record EvalCaseOrigin(
    String sourceType,
    String sourceId,
    String note,
    Map<String, Object> metadata
) {
    public EvalCaseOrigin {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public static EvalCaseOrigin empty() {
        return new EvalCaseOrigin(null, null, null, Map.of());
    }
}
