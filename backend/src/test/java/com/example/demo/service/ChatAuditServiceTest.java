package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.api.ApiException;
import com.example.demo.service.audit.port.ChatAuditRepository;
import com.example.demo.service.audit.StoredChatAuditRunRecord;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.AppliedInstruction;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionScopeLevel;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgePresetReference;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalTrace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatAuditServiceTest {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Test
    void recordsFullAuditSnapshot() throws Exception {
        ChatAuditRepository repository = mock(ChatAuditRepository.class);
        AtomicReference<StoredChatAuditRunRecord> storedRecord = new AtomicReference<>();
        doAnswer(invocation -> {
            storedRecord.set(invocation.getArgument(0));
            return null;
        }).when(repository).save(any());

        ChatAuditService service = service(repository);
        ChatExecutionResponse response = responseFixture();

        String recordedId = service.record(response);

        assertNotNull(recordedId);
        verify(repository).save(any());
        assertEquals(recordedId, storedRecord.get().id());

        JsonNode auditJson = JSON_MAPPER.readTree(storedRecord.get().auditJson());
        assertEquals("with_quotes", auditJson.get("answerMode").asText());
        assertTrue(auditJson.has("instructionTrace"));
        assertTrue(auditJson.has("knowledgeScopeResolved"));
        assertTrue(auditJson.has("retrievalTrace"));
        assertTrue(auditJson.has("sources"));
    }

    @Test
    void delegatesAuditReadsToQueryService() {
        ChatAuditRepository repository = mock(ChatAuditRepository.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatAuditService service = new ChatAuditService(
            repository,
            queryService,
            mockTraceService()
        );
        String runId = UUID.randomUUID().toString();

        service.getRun(runId);
        service.listRuns();
        service.getTrace(runId);

        verify(queryService).getRun(runId);
        verify(queryService).listRuns();
        verify(queryService).getTrace(runId);
        verifyNoInteractions(repository);
    }

    @Test
    void currentHealthMirrorsTraceHealth() {
        ChatRunTraceService traceService = mockTraceService();
        when(traceService.currentHealth()).thenReturn(new ChatRunTraceService.TraceHealth(
            "DOWN",
            "chat_trace.storage_failed",
            "trace unavailable",
            2,
            "2026-04-19T00:00:00Z"
        ));
        ChatAuditService service = new ChatAuditService(
            mock(ChatAuditRepository.class),
            mock(ChatRunQueryService.class),
            traceService
        );

        assertEquals("DOWN", service.currentHealth().status());
        assertEquals("chat_trace.storage_failed", service.currentHealth().reasonCode());
        assertEquals(2, service.currentHealth().consecutiveFailureCount());
    }

    @Test
    void recordPropagatesRepositoryFailures() {
        ChatAuditRepository repository = mock(ChatAuditRepository.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("audit table unavailable");
        }).when(repository).save(any());
        ChatAuditService service = service(repository);

        assertThrows(IllegalStateException.class, () -> service.record(responseFixture()));
    }

    private ChatAuditService service(ChatAuditRepository repository) {
        return new ChatAuditService(
            repository,
            mock(ChatRunQueryService.class),
            mockTraceService()
        );
    }

    private ChatRunTraceService mockTraceService() {
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        when(traceService.currentHealth()).thenReturn(new ChatRunTraceService.TraceHealth("UP", null, null, 0, null));
        return traceService;
    }

    private ChatExecutionResponse responseFixture() {
        return new ChatExecutionResponse(
            ChatMode.RAG,
            "qwen2.5:7b",
            "Какой тариф действует для North Upgrade и какие документы это подтверждают? ".repeat(4).trim(),
            "Тариф Премиум стоит 12000 тенге. ".repeat(10).trim(),
            "ready",
            "2026-04-19T00:00:00Z",
            10,
            20,
            30,
            AnswerMode.WITH_QUOTES,
            List.of(new AppliedInstruction(
                "instruction-1",
                "Факты только из контекста",
                InstructionCategory.CONTEXT,
                InstructionScopeLevel.CHAT_SCENARIO,
                null,
                3
            )),
            List.of(new InstructionTraceEntry(
                "instruction-1",
                "Факты только из контекста",
                InstructionCategory.CONTEXT,
                InstructionScopeLevel.CHAT_SCENARIO,
                null,
                3,
                true,
                false,
                "Только документированные факты."
            )),
            new KnowledgeScopeResolved(
                List.of(new KnowledgePresetReference("preset-1", "Договоры", 2)),
                List.of(KnowledgeDocumentClass.CONTRACTS),
                List.of("finance"),
                "north-upgrade",
                true
            ),
            new RetrievalTrace(2, 2, 2, 1, 1, 1, 4, 2, 1, "sufficient"),
            List.of(new ChatSource(
                "material-1",
                "material-1:0",
                "North contract",
                "Тариф Премиум стоит 12000 тенге.",
                100,
                1.0d,
                List.of("тариф", "12000"),
                "/api/materials/material-1?chunkId=material-1%3A0&chunkIndex=0&page=2",
                0,
                2,
                "direct-text",
                false,
                0.1d,
                1.5d
            )),
            null
        );
    }
}
