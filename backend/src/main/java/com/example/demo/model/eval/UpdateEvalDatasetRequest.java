package com.example.demo.model.eval;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateEvalDatasetRequest(
    @Size(max = 256)
    String name,
    @Size(max = 2_000)
    String description,
    @Size(max = 64)
    List<@Size(max = 64) String> tags
) {
    public UpdateEvalDatasetRequest {
        tags = tags == null ? null : List.copyOf(tags);
    }
}
