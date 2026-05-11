package com.example.demo.model.eval;

public record EvalCompareRequest(
    String baselineRunId,
    String candidateRunId
) {
}
