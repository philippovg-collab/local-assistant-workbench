package com.example.demo.model.eval;

import jakarta.validation.constraints.Size;

public record SubmitEvalCaseReviewRequest(
    @Size(max = 2_000)
    String note
) {
}
