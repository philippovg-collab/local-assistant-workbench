package com.example.demo.model.eval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateEvalDatasetRequest(
    @NotBlank
    @Size(max = 128)
    String datasetKey,
    @NotNull
    EvalDatasetKind kind,
    @Size(max = 128)
    String version,
    @NotBlank
    @Size(max = 256)
    String name,
    @Size(max = 2_000)
    String description,
    @Size(max = 64)
    List<@Size(max = 64) String> tags
) {
    public CreateEvalDatasetRequest {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
