package com.example.demo.model.eval;

import jakarta.validation.constraints.Size;

public record CreateEvalDatasetVersionRequest(
    @Size(max = 128)
    String version,
    @Size(max = 2_000)
    String note
) {
}
