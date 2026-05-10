package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.model.RetrievalQueryReferencedSource;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.model.RetrievalQueryResolutionDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditRedactionServiceTest {

    private final AuditRedactionService redactionService = new AuditRedactionService(new ChatAuditProperties());

    @Test
    void redactsSensitiveMetadataValuesByKeyAtEveryDepth() {
        Map<String, Object> metadata = redactionService.redactMetadata(Map.of(
            "password", "top-secret",
            "nested", Map.of(
                "apiKey", "secret-key",
                "safe", "Authorization: Bearer token-123"
            ),
            "items", List.of(Map.of("refresh_token", "refresh-secret"))
        ));

        assertEquals("[REDACTED]", metadata.get("password"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) metadata.get("nested");
        assertEquals("[REDACTED]", nested.get("apiKey"));
        assertEquals("Authorization: Bearer [REDACTED]", nested.get("safe"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) metadata.get("items");
        assertEquals("[REDACTED]", items.getFirst().get("refresh_token"));
        assertFalse(metadata.toString().contains("top-secret"));
        assertFalse(metadata.toString().contains("secret-key"));
        assertFalse(metadata.toString().contains("refresh-secret"));
    }

    @Test
    void redactsContextSnapshotHistoryResolverMemoryAndStickyPayloads() {
        String runId = UUID.randomUUID().toString();
        String conversationId = UUID.randomUUID().toString();
        ContextAssemblySnapshotDetail redacted = redactionService.redactContextAssemblySnapshot(new ContextAssemblySnapshotDetail(
            UUID.randomUUID().toString(),
            runId,
            conversationId,
            2,
            ChatMode.RAG,
            "Authorization: Bearer prompt-token",
            "password=resolved-secret",
            List.of(new ContextAssemblyHistoryItem(
                UUID.randomUUID().toString(),
                1,
                "assistant",
                "answer api_key=history-secret",
                10,
                Instant.parse("2026-05-10T00:00:00Z")
            )),
            List.of(),
            List.of(new ContextAssemblyMemoryItem(
                UUID.randomUUID().toString(),
                MemoryEntryType.USER_PREFERENCE,
                "call me with token=memory-secret",
                "workspace",
                "project",
                false,
                BigDecimal.ONE,
                8,
                Instant.parse("2026-05-10T00:00:01Z")
            )),
            List.of(),
            null,
            "hash",
            false,
            new RetrievalQueryResolution(
                "Bearer original-secret",
                "apiKey=query-secret",
                "password=rewritten-secret",
                RetrievalQueryResolutionDecision.RESOLVED,
                0.9d,
                List.of("marker"),
                List.of(runId),
                List.of(new RetrievalQueryReferencedSource(
                    1,
                    1,
                    "material-1",
                    "Doc token=title-secret",
                    "N-1",
                    "Project",
                    "Counterparty",
                    7
                )),
                false,
                null
            ),
            Map.of("accessToken", "sticky-secret"),
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-05-10T00:00:02Z")
        ));

        String serialized = redacted.toString();
        assertFalse(serialized.contains("prompt-token"));
        assertFalse(serialized.contains("resolved-secret"));
        assertFalse(serialized.contains("history-secret"));
        assertFalse(serialized.contains("memory-secret"));
        assertFalse(serialized.contains("query-secret"));
        assertFalse(serialized.contains("rewritten-secret"));
        assertFalse(serialized.contains("sticky-secret"));
        assertEquals("[REDACTED]", ((Map<?, ?>) redacted.stickyResolution()).get("accessToken"));
    }
}
