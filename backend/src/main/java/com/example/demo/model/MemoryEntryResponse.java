package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemoryEntryResponse(
    String id,
    MemoryEntryStatus status,
    MemoryEntryType entryType,
    String contentText,
    String normalizedKey,
    String workspaceKey,
    String projectKey,
    Boolean pinned,
    BigDecimal confidence,
    Map<String, Object> provenance,
    String sourceConversationId,
    String sourceRunId,
    Integer sourceTurnNo,
    String sourceTextPreview,
    String sourceTextHash,
    Instant approvedAt,
    Instant rejectedAt,
    Instant deletedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public MemoryEntryResponse {
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
