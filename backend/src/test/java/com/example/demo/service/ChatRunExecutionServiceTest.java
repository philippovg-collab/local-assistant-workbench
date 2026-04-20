package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.infrastructure.audit.ChatRunQueueLease;
import com.example.demo.infrastructure.audit.EnqueuedChatRun;
import com.example.demo.infrastructure.audit.PostgresChatRunQueueRepository;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("unchecked")
class ChatRunExecutionServiceTest {

    @Test
    void submitDurablyEnqueuesRunBeforeSchedulingLocalWorker() {
        ManualExecutorService executor = new ManualExecutorService();
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatRunExecutionService service = service(executor, queueRepository);
        ChatExecutionRequest request = request();
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        when(queueRepository.enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));

        ChatRunSubmissionResponse response = service.submit(request);

        assertEquals(runId, response.id());
        assertEquals("RECEIVED", response.status());
        assertEquals(1, executor.taskCount());
        verify(queueRepository).enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class));
    }

    @Test
    void executorRejectionAfterDurableEnqueueDoesNotFailSubmission() {
        RejectingExecutorService executor = new RejectingExecutorService();
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunExecutionService service = service(executor, queueRepository, mock(ChatExecutionService.class), traceService, mock(ChatRunQueryService.class));
        ChatExecutionRequest request = request();
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        when(queueRepository.enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));

        ChatRunSubmissionResponse response = service.submit(request);

        assertEquals(runId, response.id());
        verify(traceService, never()).failRun(any(), eq("QUEUE"), any());
    }

    @Test
    void applicationReadyDrainsPendingRunFromDurableQueue() {
        ManualExecutorService executor = new ManualExecutorService();
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            executor,
            queueRepository,
            chatExecutionService,
            mock(ChatRunTraceService.class),
            queryService
        );
        String runId = UUID.randomUUID().toString();
        ChatExecutionRequest request = request();
        ChatRunQueueLease lease = lease(runId, request);
        when(queueRepository.claimNext(any(Instant.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "COMPLETED"));

        service.onApplicationReady();
        executor.runNext();

        verify(queueRepository).recoverStaleClaims(any(Instant.class), any(Instant.class));
        verify(chatExecutionService).executeWithTraceContext(eq(request), any(ChatRunTraceService.RunTraceContext.class));
        verify(queueRepository).deleteQueueEntry(runId);
    }

    @Test
    void runtimeFailureMarksRunFailedWithoutRequeueing() {
        ManualExecutorService executor = new ManualExecutorService();
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunExecutionService service = service(
            executor,
            queueRepository,
            chatExecutionService,
            traceService,
            mock(ChatRunQueryService.class)
        );
        String runId = UUID.randomUUID().toString();
        ChatRunQueueLease lease = lease(runId, request());
        RuntimeException failure = new IllegalStateException("llm down");
        when(queueRepository.claimNext(any(Instant.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(traceService.failRun(any(ChatRunTraceService.RunTraceContext.class), eq("EXECUTION"), eq(failure)))
            .thenReturn(true);
        when(chatExecutionService.executeWithTraceContext(eq(lease.request()), any(ChatRunTraceService.RunTraceContext.class)))
            .thenThrow(failure);

        service.requestProcessing();
        executor.runNext();

        verify(traceService).failRun(any(ChatRunTraceService.RunTraceContext.class), eq("EXECUTION"), eq(failure));
        verify(queueRepository).deleteQueueEntry(runId);
    }

    @Test
    void cancelPendingRunTransitionsTraceAndDeletesQueueEntry() {
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            new ManualExecutorService(),
            queueRepository,
            mock(ChatExecutionService.class),
            traceService,
            queryService
        );
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "RECEIVED"));
        when(traceService.cancelRun(runId, createdAt)).thenReturn(true);

        service.cancel(runId);

        verify(traceService).cancelRun(runId, createdAt);
        verify(queueRepository).deleteQueueEntry(runId);
    }

    @Test
    void cancelRunningRunInterruptsLocalThreadBestEffort() {
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            new ManualExecutorService(),
            queueRepository,
            mock(ChatExecutionService.class),
            traceService,
            queryService
        );
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        Thread runningThread = new Thread(() -> {
        });
        MapAccessor.runningThreads(service).put(runId, runningThread);
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "PROMPT"));
        when(traceService.cancelRun(runId, createdAt)).thenAnswer(invocation -> {
            assertFalse(runningThread.isInterrupted());
            return true;
        });

        service.cancel(runId);

        assertTrue(runningThread.isInterrupted());
        verify(queueRepository).deleteQueueEntry(runId);
    }

    @Test
    void cancelRejectedByTerminalTransitionDoesNotInterruptOrDeleteQueueEntry() {
        PostgresChatRunQueueRepository queueRepository = mock(PostgresChatRunQueueRepository.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            new ManualExecutorService(),
            queueRepository,
            mock(ChatExecutionService.class),
            traceService,
            queryService
        );
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        Thread runningThread = new Thread(() -> {
        });
        MapAccessor.runningThreads(service).put(runId, runningThread);
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "PROMPT"));
        when(traceService.cancelRun(runId, createdAt)).thenReturn(false);

        service.cancel(runId);

        assertFalse(runningThread.isInterrupted());
        assertTrue(MapAccessor.runningThreads(service).containsKey(runId));
        verify(queueRepository, never()).deleteQueueEntry(runId);
    }

    private ChatRunExecutionService service(
        ManualExecutorService executor,
        PostgresChatRunQueueRepository queueRepository
    ) {
        return service(
            executor,
            queueRepository,
            mock(ChatExecutionService.class),
            mock(ChatRunTraceService.class),
            mock(ChatRunQueryService.class)
        );
    }

    private ChatRunExecutionService service(
        AbstractExecutorService executor,
        PostgresChatRunQueueRepository queueRepository,
        ChatExecutionService chatExecutionService,
        ChatRunTraceService traceService,
        ChatRunQueryService queryService
    ) {
        ChatExecutionProperties properties = new ChatExecutionProperties();
        properties.setThreads(1);
        properties.setClaimLeaseSeconds(300);
        return new ChatRunExecutionService(
            executor,
            properties,
            queueRepository,
            chatExecutionService,
            traceService,
            queryService
        );
    }

    private ChatExecutionRequest request() {
        return new ChatExecutionRequest(ChatMode.DIRECT, "qwen2.5:7b", "Hello", null, List.of());
    }

    private ChatRunQueueLease lease(String runId, ChatExecutionRequest request) {
        return new ChatRunQueueLease(
            runId,
            request,
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:01Z"),
            1
        );
    }

    private ChatRunTraceDetail trace(String runId, String status) {
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        return new ChatRunTraceDetail(
            runId,
            ChatMode.DIRECT,
            status,
            "qwen2.5:7b",
            null,
            null,
            null,
            null,
            createdAt,
            "COMPLETED".equals(status) ? Instant.parse("2026-04-19T00:00:02Z") : null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of()
        );
    }

    private static class ManualExecutorService extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pendingTasks = List.copyOf(tasks);
            tasks.clear();
            return pendingTasks;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int taskCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static class RejectingExecutorService extends ManualExecutorService {
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("full");
        }
    }

    private static class MapAccessor {
        @SuppressWarnings("unchecked")
        static java.util.Map<String, Thread> runningThreads(ChatRunExecutionService service) {
            return (java.util.Map<String, Thread>) ReflectionTestUtils.getField(service, "runningThreads");
        }
    }
}
