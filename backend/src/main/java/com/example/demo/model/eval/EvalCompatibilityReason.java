package com.example.demo.model.eval;

import java.util.Map;

public record EvalCompatibilityReason(
    String code,
    String message,
    EvalCompatibilityStatus status,
    Map<String, Object> details
) {
    public EvalCompatibilityReason {
        details = details == null ? Map.of() : details;
    }
}
