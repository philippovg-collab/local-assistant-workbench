package com.example.demo.model.eval;

public record ResolvedEvalExecutionConfig(
    EvalExecutionConfig executionConfig,
    String configHash,
    EvalRuntimeStateSnapshot runtimeState
) {
}
