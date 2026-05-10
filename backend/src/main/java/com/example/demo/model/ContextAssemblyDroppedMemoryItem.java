package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextAssemblyDroppedMemoryItem(
    String id,
    MemoryEntryType entryType,
    String workspaceKey,
    String projectKey,
    Boolean pinned,
    String reason,
    Integer estimatedTokens
) {
}
