package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.audit.PostgresChatAuditRepository;
import com.example.demo.infrastructure.audit.PostgresChatRunTraceRepository;
import com.example.demo.infrastructure.audit.StoredChatAuditRunRecord;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRunQueryServiceTest {

    @Test
    void listRunsMergesModernAndLegacyRunsBeforeSortingDedupingAndLimiting() {
        PostgresChatRunTraceRepository traceRepository = mock(PostgresChatRunTraceRepository.class);
        PostgresChatAuditRepository legacyRepository = mock(PostgresChatAuditRepository.class);
        String sharedId = UUID.randomUUID().toString();
        String modernOnlyId = UUID.randomUUID().toString();
        String legacyOnlyId = UUID.randomUUID().toString();

        when(traceRepository.findRunSummaries(20)).thenReturn(List.of(
            summary(modernOnlyId, "modern older", Instant.parse("2026-04-19T00:00:01Z")),
            summary(sharedId, "modern shared", Instant.parse("2026-04-19T00:00:03Z"))
        ));
        when(legacyRepository.findAll(20)).thenReturn(List.of(
            legacyRecord(legacyOnlyId, "legacy newest", Instant.parse("2026-04-19T00:00:04Z")),
            legacyRecord(sharedId, "legacy duplicate", Instant.parse("2026-04-19T00:00:02Z"))
        ));

        List<ChatAuditRunSummary> runs = new ChatRunQueryService(traceRepository, legacyRepository).listRuns();

        assertEquals(List.of(legacyOnlyId, sharedId, modernOnlyId), runs.stream().map(ChatAuditRunSummary::id).toList());
        assertEquals(List.of("legacy newest", "modern shared", "modern older"), runs.stream().map(ChatAuditRunSummary::promptPreview).toList());
        verify(traceRepository).findRunSummaries(20);
        verify(legacyRepository).findAll(20);
    }

    private ChatAuditRunSummary summary(String id, String promptPreview, Instant createdAt) {
        return new ChatAuditRunSummary(
            id,
            ChatMode.RAG,
            "qwen2.5:7b",
            null,
            promptPreview,
            "answer",
            createdAt,
            "COMPLETED",
            null,
            null,
            null,
            null
        );
    }

    private StoredChatAuditRunRecord legacyRecord(String id, String prompt, Instant createdAt) {
        return new StoredChatAuditRunRecord(
            id,
            ChatMode.RAG,
            "qwen2.5:7b",
            prompt,
            "answer",
            "ready",
            null,
            "{}",
            createdAt
        );
    }
}
