package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextAssemblyMemoryItem(
    String id,
    MemoryEntryType entryType,
    String contentText,
    String workspaceKey,
    String projectKey,
    Boolean pinned,
    BigDecimal confidence,
    Integer estimatedTokens,
    Instant updatedAt
) {
}
