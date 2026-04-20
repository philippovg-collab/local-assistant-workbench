package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.audit.PostgresChatAuditRepository;
import com.example.demo.infrastructure.audit.PostgresChatRunTraceRepository;
import com.example.demo.infrastructure.audit.StoredChatAuditRunRecord;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatRunRequestSnapshot;
import com.example.demo.model.ChatRunTraceDetail;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ChatRunQueryServiceTest {

    private PostgresChatRunTraceRepository traceRepository;
    private PostgresChatAuditRepository legacyRepository;
    private ChatRunQueryService service;

    @BeforeEach
    void setUp() {
        traceRepository = mock(PostgresChatRunTraceRepository.class);
        legacyRepository = mock(PostgresChatAuditRepository.class);
        service = new ChatRunQueryService(traceRepository, legacyRepository);
    }

    @Test
    void listRunsMergesModernAndLegacyRunsBeforeSortingDedupingAndLimiting() {
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

        List<ChatAuditRunSummary> runs = service.listRuns();

        assertEquals(List.of(legacyOnlyId, sharedId, modernOnlyId), runs.stream().map(ChatAuditRunSummary::id).toList());
        assertEquals(List.of("legacy newest", "modern shared", "modern older"), runs.stream().map(ChatAuditRunSummary::promptPreview).toList());
        verify(traceRepository).findRunSummaries(20);
        verify(legacyRepository).findAll(20);
    }

    @Test
    void getResultRejectsCompletedTraceWithoutOutput() {
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(runId)).thenReturn(Optional.of(header(runId, "COMPLETED", null)));
        when(traceRepository.findResult(runId)).thenReturn(Optional.empty());
        when(traceRepository.findTrace(runId)).thenReturn(Optional.of(trace(
            runId,
            "COMPLETED",
            requestSnapshot("What happened?"),
            null,
            null
        )));

        ApiException exception = assertThrows(ApiException.class, () -> service.getResult(runId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("chat_run.result_unavailable", exception.getCode());
        assertEquals("Chat run completed but result output is unavailable", exception.getMessage());
    }

    @Test
    void getResultRejectsCompletedTraceWithoutRequestSnapshot() {
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(runId)).thenReturn(Optional.of(header(runId, "COMPLETED", null)));
        when(traceRepository.findResult(runId)).thenReturn(Optional.empty());
        when(traceRepository.findTrace(runId)).thenReturn(Optional.of(trace(
            runId,
            "COMPLETED",
            null,
            output("Answer"),
            null
        )));

        ApiException exception = assertThrows(ApiException.class, () -> service.getResult(runId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("chat_run.result_unavailable", exception.getCode());
        assertEquals("Chat run completed but result output is unavailable", exception.getMessage());
    }

    @Test
    void getResultRejectsCompletedTraceWithoutFinalAnswer() {
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(runId)).thenReturn(Optional.of(header(runId, "COMPLETED", null)));
        when(traceRepository.findResult(runId)).thenReturn(Optional.empty());
        when(traceRepository.findTrace(runId)).thenReturn(Optional.of(trace(
            runId,
            "COMPLETED",
            requestSnapshot("What happened?"),
            output(null),
            null
        )));

        ApiException exception = assertThrows(ApiException.class, () -> service.getResult(runId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("chat_run.result_unavailable", exception.getCode());
        assertEquals("Chat run completed but result output is unavailable", exception.getMessage());
    }

    @Test
    void getResultReturnsStoredResultForCompletedTrace() {
        String runId = UUID.randomUUID().toString();
        ChatExecutionResponse storedResponse = response(runId, "Stored answer");
        when(traceRepository.findHeaderStatus(runId)).thenReturn(Optional.of(header(runId, "COMPLETED", null)));
        when(traceRepository.findResult(runId)).thenReturn(Optional.of(storedResponse));

        ChatExecutionResponse response = service.getResult(runId);

        assertEquals(storedResponse, response);
        verify(traceRepository, never()).findTrace(any());
        verify(traceRepository, never()).insertResultIfAbsent(any(), any(), any(), any());
    }

    @Test
    void getResultReturnsReconstructedResponseAndBackfillsWhenStoredResultIsAbsent() {
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(runId)).thenReturn(Optional.of(header(runId, "COMPLETED", null)));
        when(traceRepository.findResult(runId)).thenReturn(Optional.empty());
        when(traceRepository.findTrace(runId)).thenReturn(Optional.of(trace(
            runId,
            "COMPLETED",
            requestSnapshot("What happened?"),
            output("Answer"),
            null
        )));

        ChatExecutionResponse response = service.getResult(runId);

        assertEquals(ChatMode.DIRECT, response.mode());
        assertEquals("qwen2.5:7b", response.model());
        assertEquals("What happened?", response.prompt());
        assertEquals("Answer", response.answer());
        assertEquals(runId, response.auditRunId());
        verify(traceRepository).insertResultIfAbsent(
            runId,
            response,
            Instant.parse("2026-04-19T00:00:01Z"),
            "TRACE_BACKFILL"
        );
    }

    @Test
    void getResultReturnsReconstructedResponseWhenLazyBackfillFails() {
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(runId)).thenReturn(Optional.of(header(runId, "COMPLETED", null)));
        when(traceRepository.findResult(runId)).thenReturn(Optional.empty());
        when(traceRepository.findTrace(runId)).thenReturn(Optional.of(trace(
            runId,
            "COMPLETED",
            requestSnapshot("What happened?"),
            output("Answer"),
            null
        )));
        doThrow(new IllegalStateException("storage down")).when(traceRepository).insertResultIfAbsent(
            any(),
            any(),
            any(),
            any()
        );

        ChatExecutionResponse response = service.getResult(runId);

        assertEquals("Answer", response.answer());
        assertEquals(runId, response.auditRunId());
    }

    @Test
    void getResultKeepsExistingFailureAndInProgressErrors() {
        String failedRunId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(failedRunId)).thenReturn(Optional.of(header(
            failedRunId,
            "FAILED",
            "model exploded"
        )));
        ApiException failed = assertThrows(ApiException.class, () -> service.getResult(failedRunId));
        assertEquals(HttpStatus.CONFLICT, failed.getStatus());
        assertEquals("chat_run.failed", failed.getCode());
        assertEquals("model exploded", failed.getMessage());

        String cancelledRunId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(cancelledRunId)).thenReturn(Optional.of(header(
            cancelledRunId,
            "CANCELLED",
            null
        )));
        ApiException cancelled = assertThrows(ApiException.class, () -> service.getResult(cancelledRunId));
        assertEquals(HttpStatus.CONFLICT, cancelled.getStatus());
        assertEquals("chat_run.cancelled", cancelled.getCode());

        String runningRunId = UUID.randomUUID().toString();
        when(traceRepository.findHeaderStatus(runningRunId)).thenReturn(Optional.of(header(
            runningRunId,
            "LLM_DONE",
            null
        )));
        ApiException running = assertThrows(ApiException.class, () -> service.getResult(runningRunId));
        assertEquals(HttpStatus.CONFLICT, running.getStatus());
        assertEquals("chat_run.not_completed", running.getCode());
        verify(traceRepository, never()).findResult(any());
        verify(traceRepository, never()).findTrace(any());
        verify(traceRepository, never()).insertResultIfAbsent(any(), any(), any(), any());
    }

    private PostgresChatRunTraceRepository.ChatRunHeaderStatus header(
        String id,
        String status,
        String failureMessage
    ) {
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        Instant completedAt = "COMPLETED".equals(status)
            ? Instant.parse("2026-04-19T00:00:01Z")
            : null;
        return new PostgresChatRunTraceRepository.ChatRunHeaderStatus(
            id,
            status,
            createdAt,
            completedAt,
            failureMessage
        );
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

    private ChatRunTraceDetail trace(
        String id,
        String status,
        ChatRunRequestSnapshot requestSnapshot,
        ChatRunOutputTrace output,
        String failureMessage
    ) {
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        Instant completedAt = "COMPLETED".equals(status)
            ? Instant.parse("2026-04-19T00:00:01Z")
            : null;
        Instant failedAt = "FAILED".equals(status) || "CANCELLED".equals(status)
            ? Instant.parse("2026-04-19T00:00:01Z")
            : null;
        return new ChatRunTraceDetail(
            id,
            ChatMode.DIRECT,
            status,
            "qwen2.5:7b",
            "qwen2.5:7b",
            null,
            null,
            null,
            createdAt,
            completedAt,
            failedAt,
            null,
            failureMessage == null ? null : "LLM",
            failureMessage == null ? null : "chat_trace.execution_failed",
            failureMessage,
            requestSnapshot,
            null,
            null,
            List.of(),
            output,
            List.of()
        );
    }

    private ChatRunRequestSnapshot requestSnapshot(String prompt) {
        return new ChatRunRequestSnapshot(null, null, prompt, null, null);
    }

    private ChatRunOutputTrace output(String finalAnswer) {
        return new ChatRunOutputTrace(null, finalAnswer, List.of(), null, false, false);
    }

    private ChatExecutionResponse response(String runId, String answer) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "What happened?",
            answer,
            "ready",
            "2026-04-19T00:00:01Z",
            1,
            2,
            3,
            null,
            List.of(),
            List.of(),
            com.example.demo.model.KnowledgeScopeResolved.empty(),
            new com.example.demo.model.RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            List.of(),
            runId
        );
    }
}
