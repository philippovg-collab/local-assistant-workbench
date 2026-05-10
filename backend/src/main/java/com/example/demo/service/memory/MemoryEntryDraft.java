package com.example.demo.service.memory;

import com.example.demo.model.MemoryEntryType;
import java.math.BigDecimal;
import java.util.Map;

public record MemoryEntryDraft(
    MemoryEntryType entryType,
    String contentText,
    String normalizedKey,
    String workspaceKey,
    String projectKey,
    boolean pinned,
    BigDecimal confidence,
    Map<String, Object> provenance,
    String sourceConversationId,
    String sourceRunId,
    Integer sourceTurnNo,
    String sourceTextPreview,
    String sourceTextHash
) {
    public MemoryEntryDraft {
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
