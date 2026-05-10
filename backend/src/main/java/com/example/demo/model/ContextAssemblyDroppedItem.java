package com.example.demo.model;

public record ContextAssemblyDroppedItem(
    String runId,
    Integer turnNo,
    String reason,
    Integer estimatedTokens
) {
}
