package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.config.ChatAuditProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.LlmCallTrace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class ChatRunTraceServiceTest {

    @Test
    void completeRunWritesCompletedEventOnlyWhenTransitionSucceeds() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        when(repository.completeRun(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong(),
            eq(null)
        )).thenReturn(true);

        boolean completed = service.completeRun(context, "qwen2.5:7b", AnswerMode.BRIEF, "ready");

        assertTrue(completed);
        verify(repository).insertEvent(eq(context.id()), eq("COMPLETED"), eq(Map.of()), any(Instant.class));
    }

    @Test
    void completeRunDoesNotWriteCompletedEventWhenTransitionIsRejected() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        when(repository.completeRun(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong(),
            eq(null)
        )).thenReturn(false);

        boolean completed = service.completeRun(context, "qwen2.5:7b", AnswerMode.BRIEF, "ready");

        assertFalse(completed);
        verify(repository, never()).insertEvent(eq(context.id()), eq("COMPLETED"), any(), any(Instant.class));
    }

    @Test
    void completeRunWithResultDelegatesAtomicTerminalResultWrite() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        ChatExecutionResponse response = response(context.id());
        when(repository.completeRunWithResult(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong(),
            eq(response),
            eq(null)
        )).thenReturn(true);

        boolean completed = service.completeRunWithResult(context, response);

        assertTrue(completed);
        verify(repository).completeRunWithResult(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong(),
            eq(response),
            eq(null)
        );
        verify(repository, never()).insertEvent(eq(context.id()), eq("COMPLETED"), any(), any(Instant.class));
    }

    @Test
    void completeRunWithResultReturnsFalseWhenAtomicTransitionIsRejected() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        ChatExecutionResponse response = response(context.id());
        when(repository.completeRunWithResult(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong(),
            eq(response),
            eq(null)
        )).thenReturn(false);

        boolean completed = service.completeRunWithResult(context, response);

        assertFalse(completed);
        verify(repository, never()).insertEvent(eq(context.id()), eq("COMPLETED"), any(), any(Instant.class));
    }

    @Test
    void failRunWritesFailedEventOnlyWhenTransitionSucceeds() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        RuntimeException failure = new IllegalStateException("boom");
        when(repository.failRun(
            eq(context.id()),
            eq("LLM"),
            eq("chat_trace.execution_failed"),
            eq("boom"),
            any(Instant.class),
            anyLong(),
            eq(null)
        )).thenReturn(true);

        boolean failed = service.failRun(context, "LLM", failure);

        assertTrue(failed);
        verify(repository).insertEvent(
            eq(context.id()),
            eq("FAILED"),
            eq(Map.of(
                "stage",
                "LLM",
                "code",
                "chat_trace.execution_failed",
                "message",
                "boom"
            )),
            any(Instant.class)
        );
    }

    @Test
    void failRunPreservesNestedCodedExceptionReasonCode() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        RuntimeException failure = new IllegalStateException(
            "wrapped",
            new ProviderException(
                ErrorType.PROVIDER_TIMEOUT,
                "llm.provider_timeout",
                "Provider timed out"
            )
        );
        when(repository.failRun(
            eq(context.id()),
            eq("LLM"),
            eq("llm.provider_timeout"),
            eq("Provider timed out"),
            any(Instant.class),
            anyLong(),
            eq(null)
        )).thenReturn(true);

        boolean failed = service.failRun(context, "LLM", failure);

        assertTrue(failed);
        verify(repository).insertEvent(
            eq(context.id()),
            eq("FAILED"),
            eq(Map.of(
                "stage",
                "LLM",
                "code",
                "llm.provider_timeout",
                "message",
                "Provider timed out"
            )),
            any(Instant.class)
        );
    }

    @Test
    void failRunDoesNotWriteFailedEventWhenTransitionIsRejected() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        RuntimeException failure = new IllegalStateException("boom");
        when(repository.failRun(
            eq(context.id()),
            eq("LLM"),
            eq("chat_trace.execution_failed"),
            eq("boom"),
            any(Instant.class),
            anyLong(),
            eq(null)
        )).thenReturn(false);

        boolean failed = service.failRun(context, "LLM", failure);

        assertFalse(failed);
        verify(repository, never()).insertEvent(eq(context.id()), eq("FAILED"), any(), any(Instant.class));
    }

    @Test
    void nonTerminalStageEventsUseMutableRunGuard() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "prompt",
            null,
            List.of()
        );

        service.saveRequestSnapshot(context, request, request);

        verify(repository).saveRequestSnapshot(context.id(), request, request, null);
        verify(repository).insertEventIfRunMutable(
            eq(context.id()),
            eq("REQUEST_SNAPSHOT_SAVED"),
            eq(Map.of()),
            any(Instant.class),
            isNull()
        );
        verify(repository, never()).insertEvent(eq(context.id()), eq("REQUEST_SNAPSHOT_SAVED"), any(), any(Instant.class));
    }

    @Test
    void requestSnapshotsAreRedactedAndTruncatedBeforePersistence() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatAuditProperties properties = new ChatAuditProperties();
        properties.setMaxStoredTextChars(160);
        ChatRunTraceService service = new ChatRunTraceService(repository, new AuditRedactionService(properties));
        ChatRunTraceService.RunTraceContext context = context();
        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Authorization: Bearer token-123 password=hunter2 " + "x".repeat(200),
            "Cookie: session=secret-cookie",
            List.of()
        );

        service.saveRequestSnapshot(context, request, request);

        ArgumentCaptor<ChatExecutionRequest> requestCaptor = ArgumentCaptor.forClass(ChatExecutionRequest.class);
        ArgumentCaptor<ChatExecutionRequest> normalizedCaptor = ArgumentCaptor.forClass(ChatExecutionRequest.class);
        verify(repository).saveRequestSnapshot(
            eq(context.id()),
            requestCaptor.capture(),
            normalizedCaptor.capture(),
            isNull()
        );
        assertFalse(requestCaptor.getValue().prompt().contains("token-123"));
        assertFalse(requestCaptor.getValue().prompt().contains("hunter2"));
        assertTrue(requestCaptor.getValue().prompt().contains("[REDACTED]"));
        assertTrue(requestCaptor.getValue().prompt().length() <= 160);
        assertFalse(normalizedCaptor.getValue().systemPrompt().contains("secret-cookie"));
    }

    @Test
    void rawLlmResponseStorageIsDisabledByDefaultButFinalAnswerRemains() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();

        service.saveLlmSuccess(
            context,
            new LlmClient.ChatRequest("qwen2.5:7b", List.of(new LlmClient.Message("user", "prompt"))),
            new LlmClient.ChatResult(
                "qwen2.5:7b",
                "raw model answer",
                "2026-04-19T00:00:00Z",
                1,
                2,
                3,
                "{\"response\":\"raw model answer\"}",
                "stop",
                10L
            ),
            null
        );
        service.saveOutput(context, "raw model answer", "final user answer", List.of(), Map.of(), false, false);

        ArgumentCaptor<LlmCallTrace> llmCallCaptor = ArgumentCaptor.forClass(LlmCallTrace.class);
        verify(repository).insertLlmCall(eq(context.id()), llmCallCaptor.capture(), isNull());
        assertNull(llmCallCaptor.getValue().rawResponseText());
        assertNull(llmCallCaptor.getValue().parsedAnswerText());

        ArgumentCaptor<ChatRunOutputTrace> outputCaptor = ArgumentCaptor.forClass(ChatRunOutputTrace.class);
        verify(repository).saveOutput(eq(context.id()), outputCaptor.capture(), isNull());
        assertNull(outputCaptor.getValue().rawModelAnswer());
        assertEquals("final user answer", outputCaptor.getValue().finalUserAnswer());
    }

    @Test
    void storedMessagesAndRawOutputsAreRedactedAndTruncatedWhenRawStorageIsEnabled() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatAuditProperties properties = new ChatAuditProperties();
        properties.setStoreRawLlmResponse(true);
        properties.setMaxStoredTextChars(64);
        ChatRunTraceService service = new ChatRunTraceService(repository, new AuditRedactionService(properties));
        ChatRunTraceService.RunTraceContext context = context();

        service.saveLlmSuccess(
            context,
            new LlmClient.ChatRequest("qwen2.5:7b", List.of(new LlmClient.Message(
                "user",
                "Cookie: session=secret-cookie password=hunter2 " + "x".repeat(120)
            ))),
            new LlmClient.ChatResult(
                "qwen2.5:7b",
                "Bearer token-456 " + "answer ".repeat(30),
                "2026-04-19T00:00:00Z",
                1,
                2,
                3,
                "api_key=secret-api-key " + "raw ".repeat(30),
                "stop",
                10L
            ),
            null
        );

        ArgumentCaptor<LlmCallTrace> llmCallCaptor = ArgumentCaptor.forClass(LlmCallTrace.class);
        verify(repository).insertLlmCall(eq(context.id()), llmCallCaptor.capture(), isNull());
        LlmCallTrace call = llmCallCaptor.getValue();
        ChatRunMessage message = call.requestMessages().getFirst();
        assertFalse(message.content().contains("secret-cookie"));
        assertFalse(message.content().contains("hunter2"));
        assertFalse(call.rawResponseText().contains("secret-api-key"));
        assertFalse(call.parsedAnswerText().contains("token-456"));
        assertTrue(message.content().length() <= 64);
        assertTrue(call.rawResponseText().length() <= 64);
        assertTrue(call.parsedAnswerText().length() <= 64);
    }

    @Test
    void requestMessagesCanBeOmittedFromAuditStorage() {
        ChatRunTraceRepository repository = mock(ChatRunTraceRepository.class);
        ChatAuditProperties properties = new ChatAuditProperties();
        properties.setStoreRequestMessages(false);
        ChatRunTraceService service = new ChatRunTraceService(repository, new AuditRedactionService(properties));
        ChatRunTraceService.RunTraceContext context = context();

        service.savePromptMessages(context, List.of(new LlmClient.Message("user", "prompt")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatRunMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).savePromptMessages(eq(context.id()), messagesCaptor.capture(), isNull());
        assertTrue(messagesCaptor.getValue().isEmpty());
    }

    private ChatRunTraceService.RunTraceContext context() {
        return new ChatRunTraceService.RunTraceContext(
            UUID.randomUUID().toString(),
            Instant.parse("2026-04-19T00:00:00Z")
        );
    }

    private ChatExecutionResponse response(String runId) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "prompt",
            "answer",
            "ready",
            "2026-04-19T00:00:01Z",
            1,
            2,
            3,
            AnswerMode.BRIEF,
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
