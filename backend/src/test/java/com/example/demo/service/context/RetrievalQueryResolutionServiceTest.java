package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.MaterialMetadataProvenance;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.model.RetrievalQueryResolutionDecision;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RetrievalQueryResolutionServiceTest {

    private final ChatRunTraceRepository traceRepository = Mockito.mock(ChatRunTraceRepository.class);
    private final RetrievalQueryResolutionService service = new RetrievalQueryResolutionService(
        enabledProperties(),
        traceRepository
    );

    @Test
    void noMarkerKeepsOriginalQuery() {
        String previousRunId = "11111111-1111-1111-1111-111111111111";
        when(traceRepository.findResult(previousRunId)).thenReturn(Optional.of(ragResponse(
            previousRunId,
            "Расскажи про документы",
            List.of(source("m1", "Doc 1", "D-1", "Project", "Counterparty", 1))
        )));

        RetrievalQueryResolution resolution = service.resolve(
            request("Расскажи подробнее"),
            ChatMode.RAG,
            assembly(previousRunId, "Расскажи про документы")
        );

        assertEquals(RetrievalQueryResolutionDecision.NOT_FOLLOW_UP, resolution.decision());
        assertEquals("Расскажи подробнее", resolution.queryForRetrieval());
    }

    @Test
    void standaloneDocumentNumberKeepsOriginalQuery() {
        String previousRunId = "11111111-1111-1111-1111-111111111111";
        when(traceRepository.findResult(previousRunId)).thenReturn(Optional.of(ragResponse(
            previousRunId,
            "Расскажи про документы",
            List.of(source("m1", "Doc 1", "D-1", "Project", "Counterparty", 1))
        )));

        RetrievalQueryResolution resolution = service.resolve(
            request("а по № ABC-123 что известно?"),
            ChatMode.RAG,
            assembly(previousRunId, "Расскажи про документы")
        );

        assertEquals(RetrievalQueryResolutionDecision.NOT_FOLLOW_UP, resolution.decision());
        assertEquals("а по № ABC-123 что известно?", resolution.queryForRetrieval());
    }

    @Test
    void secondDocumentSelectsSecondDistinctDocumentMetadata() {
        String previousRunId = "11111111-1111-1111-1111-111111111111";
        when(traceRepository.findResult(previousRunId)).thenReturn(Optional.of(ragResponse(
            previousRunId,
            "Сравни документы",
            List.of(
                source("m1", "First Document", "D-1", "Alpha", "Acme", 1),
                source("m1", "First Document Duplicate", "D-1", "Alpha", "Acme", 2),
                source("m2", "Second Document", "D-2", "Beta", "Globex", 3)
            )
        )));

        RetrievalQueryResolution resolution = service.resolve(
            request("а по второму документу?"),
            ChatMode.RAG,
            assembly(previousRunId, "Сравни документы")
        );

        assertEquals(RetrievalQueryResolutionDecision.RESOLVED, resolution.decision());
        assertEquals(0.95d, resolution.confidence());
        assertEquals("D-2", resolution.referencedSources().getFirst().documentNumber());
        assertTrue(resolution.queryForRetrieval().contains("Previous user request: Сравни документы"));
        assertTrue(resolution.queryForRetrieval().contains("documentNumber=D-2"));
        assertFalse(resolution.queryForRetrieval().contains("assistant answer"));
        assertFalse(resolution.queryForRetrieval().contains("excerpt"));
    }

    @Test
    void sourceIndexSelectsRawSourceOrder() {
        String previousRunId = "11111111-1111-1111-1111-111111111111";
        when(traceRepository.findResult(previousRunId)).thenReturn(Optional.of(ragResponse(
            previousRunId,
            "Сравни источники",
            List.of(
                source("m1", "First Source", "D-1", "Alpha", "Acme", 1),
                source("m2", "Second Source", "D-2", "Beta", "Globex", 2)
            )
        )));

        RetrievalQueryResolution resolution = service.resolve(
            request("source 2 details"),
            ChatMode.RAG,
            assembly(previousRunId, "Сравни источники")
        );

        assertEquals(RetrievalQueryResolutionDecision.RESOLVED, resolution.decision());
        assertEquals(2, resolution.referencedSources().getFirst().sourceIndex());
        assertEquals("D-2", resolution.referencedSources().getFirst().documentNumber());
    }

    @Test
    void vagueDeicticWithSeveralSourcesIsLowConfidence() {
        String previousRunId = "11111111-1111-1111-1111-111111111111";
        when(traceRepository.findResult(previousRunId)).thenReturn(Optional.of(ragResponse(
            previousRunId,
            "Сравни источники",
            List.of(
                source("m1", "First Source", "D-1", "Alpha", "Acme", 1),
                source("m2", "Second Source", "D-2", "Beta", "Globex", 2)
            )
        )));

        RetrievalQueryResolution resolution = service.resolve(
            request("а там?"),
            ChatMode.RAG,
            assembly(previousRunId, "Сравни источники")
        );

        assertEquals(RetrievalQueryResolutionDecision.LOW_CONFIDENCE, resolution.decision());
        assertEquals("а там?", resolution.queryForRetrieval());
        assertTrue(resolution.degraded());
    }

    @Test
    void summaryOnlyContextDoesNotResolveFollowUp() {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");

        RetrievalQueryResolution resolution = service.resolve(
            request("а по второму документу?"),
            ChatMode.RAG,
            assemblyWithHistory(List.of(
                new ContextAssemblyHistoryItem(
                    null,
                    3,
                    "summary",
                    "Conversation summary: prior discussion mentioned two documents.",
                    8,
                    now
                )
            ))
        );

        assertEquals(RetrievalQueryResolutionDecision.NO_HISTORY, resolution.decision());
        assertEquals("а по второму документу?", resolution.queryForRetrieval());
        verifyNoInteractions(traceRepository);
    }

    private static ContextProperties enabledProperties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setRetrievalQueryResolutionEnabled(true);
        return properties;
    }

    private static ChatExecutionRequest request(String prompt) {
        return new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            prompt,
            null,
            List.of()
        );
    }

    private static PreparedContextAssembly assembly(String previousRunId, String previousPrompt) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return assemblyWithHistory(List.of(
            new ContextAssemblyHistoryItem(previousRunId, 1, "user", previousPrompt, 5, now),
            new ContextAssemblyHistoryItem(previousRunId, 1, "assistant", "assistant answer", 5, now)
        ));
    }

    private static PreparedContextAssembly assemblyWithHistory(List<ContextAssemblyHistoryItem> selectedHistory) {
        return new PreparedContextAssembly(
            true,
            null,
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333",
            2,
            ChatMode.RAG,
            "current",
            "current",
            selectedHistory,
            List.of(),
            List.of(),
            List.of(),
            new ContextTokenBudget(4, 1000, 1, 10, 0, 0),
            false,
            "ready",
            null,
            null,
            Map.of(),
            false,
            null,
            null,
            0,
            null,
            null,
            null
        );
    }

    private static ChatExecutionResponse ragResponse(
        String runId,
        String previousPrompt,
        List<ChatSource> sources
    ) {
        return new ChatExecutionResponse(
            ChatMode.RAG,
            "qwen2.5:7b",
            previousPrompt,
            "assistant answer",
            "ready",
            Instant.parse("2026-05-10T00:00:00Z").toString(),
            null,
            null,
            null,
            AnswerMode.BRIEF,
            List.of(),
            List.of(),
            KnowledgeScopeResolved.empty(),
            new RetrievalTrace(2, 2, 2, 2, 2, 2, 2, 2, sources.size(), "sufficient"),
            null,
            sources,
            runId
        );
    }

    private static ChatSource source(
        String materialId,
        String title,
        String documentNumber,
        String project,
        String counterparty,
        Integer page
    ) {
        return new ChatSource(
            materialId,
            materialId + ":" + page,
            title,
            "excerpt that must not enter resolved query",
            100,
            1.0d,
            List.of("term"),
            null,
            page,
            page,
            "test",
            false,
            DocumentBlockType.NARRATIVE,
            new MaterialMetadataSnapshot(
                DocumentType.REPORT,
                KnowledgeDocumentClass.OTHER,
                null,
                documentNumber,
                null,
                null,
                null,
                null,
                List.of(),
                SourceTrustLevel.UNKNOWN,
                project,
                "workspace",
                counterparty,
                null,
                null,
                null,
                MaterialMetadataProvenance.empty()
            ),
            null,
            null,
            null
        );
    }
}
