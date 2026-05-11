package com.example.demo.model.eval;

import java.util.List;
import java.util.Map;

public record EvalCompatibilityResult(
    EvalCompatibilityStatus status,
    List<EvalCompatibilityReason> reasons,
    Map<String, Object> summary
) {
    public EvalCompatibilityResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        summary = summary == null ? Map.of() : summary;
    }
}
