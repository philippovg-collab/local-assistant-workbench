package com.example.demo.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MemoryEntryUpdateRequest(
    MemoryEntryType entryType,
    @Size(max = 4000)
    String contentText,
    @Size(max = 256)
    String normalizedKey,
    @Size(max = 128)
    String workspaceKey,
    @Size(max = 128)
    String projectKey,
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    BigDecimal confidence,
    @Size(max = 500)
    String reason
) {
}
