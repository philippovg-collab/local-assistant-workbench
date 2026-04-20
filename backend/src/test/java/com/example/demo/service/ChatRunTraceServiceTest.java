package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.audit.PostgresChatRunTraceRepository;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRunTraceServiceTest {

    @Test
    void completeRunWritesCompletedEventOnlyWhenTransitionSucceeds() {
        PostgresChatRunTraceRepository repository = mock(PostgresChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        when(repository.completeRun(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong()
        )).thenReturn(true);

        boolean completed = service.completeRun(context, "qwen2.5:7b", AnswerMode.BRIEF, "ready");

        assertTrue(completed);
        verify(repository).insertEvent(eq(context.id()), eq("COMPLETED"), eq(Map.of()), any(Instant.class));
    }

    @Test
    void completeRunDoesNotWriteCompletedEventWhenTransitionIsRejected() {
        PostgresChatRunTraceRepository repository = mock(PostgresChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        when(repository.completeRun(
            eq(context.id()),
            eq("qwen2.5:7b"),
            eq(AnswerMode.BRIEF),
            eq("ready"),
            any(Instant.class),
            anyLong()
        )).thenReturn(false);

        boolean completed = service.completeRun(context, "qwen2.5:7b", AnswerMode.BRIEF, "ready");

        assertFalse(completed);
        verify(repository, never()).insertEvent(eq(context.id()), eq("COMPLETED"), any(), any(Instant.class));
    }

    @Test
    void failRunWritesFailedEventOnlyWhenTransitionSucceeds() {
        PostgresChatRunTraceRepository repository = mock(PostgresChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        RuntimeException failure = new IllegalStateException("boom");
        when(repository.failRun(
            eq(context.id()),
            eq("LLM"),
            eq("chat_trace.execution_failed"),
            eq("boom"),
            any(Instant.class),
            anyLong()
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
    void failRunDoesNotWriteFailedEventWhenTransitionIsRejected() {
        PostgresChatRunTraceRepository repository = mock(PostgresChatRunTraceRepository.class);
        ChatRunTraceService service = new ChatRunTraceService(repository);
        ChatRunTraceService.RunTraceContext context = context();
        RuntimeException failure = new IllegalStateException("boom");
        when(repository.failRun(
            eq(context.id()),
            eq("LLM"),
            eq("chat_trace.execution_failed"),
            eq("boom"),
            any(Instant.class),
            anyLong()
        )).thenReturn(false);

        boolean failed = service.failRun(context, "LLM", failure);

        assertFalse(failed);
        verify(repository, never()).insertEvent(eq(context.id()), eq("FAILED"), any(), any(Instant.class));
    }

    @Test
    void nonTerminalStageEventsUseMutableRunGuard() {
        PostgresChatRunTraceRepository repository = mock(PostgresChatRunTraceRepository.class);
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

        verify(repository).saveRequestSnapshot(context.id(), request, request);
        verify(repository).insertEventIfRunMutable(
            eq(context.id()),
            eq("REQUEST_SNAPSHOT_SAVED"),
            eq(Map.of()),
            any(Instant.class)
        );
        verify(repository, never()).insertEvent(eq(context.id()), eq("REQUEST_SNAPSHOT_SAVED"), any(), any(Instant.class));
    }

    private ChatRunTraceService.RunTraceContext context() {
        return new ChatRunTraceService.RunTraceContext(
            UUID.randomUUID().toString(),
            Instant.parse("2026-04-19T00:00:00Z")
        );
    }
}
