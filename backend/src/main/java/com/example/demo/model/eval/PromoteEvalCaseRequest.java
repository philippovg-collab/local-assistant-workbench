package com.example.demo.model.eval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromoteEvalCaseRequest(
    @NotBlank
    @Size(max = 36)
    String targetDatasetId,
    @Size(max = 128)
    String version,
    @Size(max = 2_000)
    String note
) {
}
