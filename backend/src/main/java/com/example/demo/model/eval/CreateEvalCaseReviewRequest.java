package com.example.demo.model.eval;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEvalCaseReviewRequest(
    @NotNull
    EvalReviewStatus status,
    @Size(max = 2_000)
    String note
) {
}
