package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.service.audit.ChatRunQueueLease;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.cancellation.ChatCancellationHandle;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.example.demo.service.cancellation.ChatRunCancelledException;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("unchecked")
class ChatRunExecutionServiceTest {

    @Test
    void submitDurablyEnqueuesRunBeforeSchedulingLocalWorker() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatRunExecutionService service = service(executor, queueRepository);
        ChatExecutionRequest request = request();
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        when(queueRepository.enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));

        ChatRunSubmissionResponse response = service.submit(request);

        assertEquals(runId, response.id());
        assertEquals("RECEIVED", response.status());
        assertEquals("/api/chat-runs/" + runId + "/status", response.statusUrl());
        assertEquals(1, executor.taskCount());
        verify(queueRepository).enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class));
    }

    @Test
    void submitRejectsInvalidNestedRequestBeforeDurableEnqueue() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatRunExecutionService service = service(executor, queueRepository);
        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.RAG,
            "qwen2.5:7b",
            "What changed?",
            null,
            List.of(),
            null,
            null,
            null,
            null,
            List.of("x".repeat(129)),
            null,
            null
        );

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.submit(request));

        assertEquals("request.field_too_large", exception.getCode());
        assertTrue(exception.getMessage().contains("chat.dismissedRetrievalHintKeys[]"));
        assertEquals(0, executor.taskCount());
        verify(queueRepository, never()).enqueue(any(), any(), any());
    }

    @Test
    void executorRejectionAfterDurableEnqueueDoesNotFailSubmission() {
        RejectingExecutorService executor = new RejectingExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
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
    void submitAndWaitReturnsPersistedResultOnCompletion() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            executor,
            queueRepository,
            mock(ChatExecutionService.class),
            mock(ChatRunTraceService.class),
            queryService
        );
        ChatExecutionRequest request = request();
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        ChatExecutionResponse expectedResponse = response(runId);
        when(queueRepository.enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));
        when(queryService.getStatus(runId)).thenReturn(status(runId, "COMPLETED", null, null));
        when(queryService.getResult(runId)).thenReturn(expectedResponse);

        ChatExecutionResponse response = service.submitAndWait(request, Duration.ofMillis(10));

        assertEquals(expectedResponse, response);
        verify(queryService).getResult(runId);
    }

    @Test
    void submitAndWaitMapsFailedRunToTypedError() {
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            new ManualExecutorService(),
            queueRepository,
            mock(ChatExecutionService.class),
            mock(ChatRunTraceService.class),
            queryService
        );
        ChatExecutionRequest request = request();
        String runId = UUID.randomUUID().toString();
        when(queueRepository.enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, Instant.parse("2026-04-19T00:00:00Z")));
        when(queryService.getStatus(runId)).thenReturn(status(runId, "FAILED", "llm.provider_unavailable", "Ollama is down"));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.submitAndWait(request, Duration.ofMillis(10)));

        assertEquals("llm.provider_unavailable", exception.getCode());
        assertEquals("Ollama is down", exception.getMessage());
    }

    @Test
    void submitAndWaitTimesOutWithoutCancellingDurableRun() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            executor,
            queueRepository,
            mock(ChatExecutionService.class),
            mock(ChatRunTraceService.class),
            queryService
        );
        ChatExecutionRequest request = request();
        String runId = UUID.randomUUID().toString();
        when(queueRepository.enqueue(eq(request), eq(ChatMode.DIRECT), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, Instant.parse("2026-04-19T00:00:00Z")));
        when(queryService.getStatus(runId)).thenReturn(status(runId, "RECEIVED", null, null));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.submitAndWait(request, Duration.ofMillis(1)));

        assertEquals("chat.run_still_processing", exception.getCode());
        assertTrue(exception.getMessage().contains("/api/chat-runs/" + runId + "/status"));
        assertEquals(1, executor.taskCount());
        verify(queueRepository, never()).deletePendingQueueEntry(runId);
        verify(queueRepository, never()).deleteQueueEntryIfOwned(any());
    }

    @Test
    void applicationReadyDrainsPendingRunFromDurableQueue() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
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
        when(queueRepository.claimNext(anyString(), any(Instant.class), any(Duration.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(queryService.getTrace(runId)).thenReturn(
            trace(runId, "RECEIVED"),
            trace(runId, "COMPLETED")
        );

        service.onApplicationReady();
        executor.runNext();

        verify(queueRepository).recoverExpiredLeases(any(Instant.class), eq(2));
        verify(chatExecutionService).executeWithTraceContext(
            eq(request),
            any(ChatRunTraceService.RunTraceContext.class),
            any(ChatCancellationToken.class)
        );
        verify(queueRepository).deleteQueueEntryIfOwned(lease);
    }

    @Test
    void runtimeFailureMarksRunFailedWithoutRequeueing() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
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
        when(queueRepository.claimNext(anyString(), any(Instant.class), any(Duration.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when((serviceQueryService(service)).getTrace(runId)).thenReturn(trace(runId, "RECEIVED"));
        when(traceService.failRun(any(ChatRunTraceService.RunTraceContext.class), eq("EXECUTION"), eq(failure)))
            .thenReturn(true);
        when(chatExecutionService.executeWithTraceContext(
            eq(lease.request()),
            any(ChatRunTraceService.RunTraceContext.class),
            any(ChatCancellationToken.class)
        ))
            .thenThrow(failure);

        service.requestProcessing();
        executor.runNext();

        verify(traceService).failRun(any(ChatRunTraceService.RunTraceContext.class), eq("EXECUTION"), eq(failure));
        verify(queueRepository).deleteQueueEntryIfOwned(lease);
    }

    @Test
    void cancelPendingRunTransitionsTraceAndDeletesQueueEntry() {
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
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
        verify(queueRepository).deletePendingQueueEntry(runId);
    }

    @Test
    void cancelRunningRunSignalsCancellationHandle() {
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
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
        ChatCancellationHandle runningRun = new ChatCancellationHandle();
        ChatRunQueueLease lease = lease(runId, request());
        MapAccessor.runningRuns(service).put(runId, new ChatRunExecutionService.RunningChatRun(lease, runningRun));
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "PROMPT"));
        when(traceService.cancelRun(runId, createdAt)).thenAnswer(invocation -> {
            assertFalse(runningRun.isCancellationRequested());
            return true;
        });

        service.cancel(runId);

        assertTrue(runningRun.isCancellationRequested());
        verify(queueRepository, never()).deletePendingQueueEntry(runId);
        verify(queueRepository, never()).deleteQueueEntryIfOwned(any());
    }

    @Test
    void cancelRejectedByTerminalTransitionDoesNotSignalOrDeleteQueueEntry() {
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
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
        ChatCancellationHandle runningRun = new ChatCancellationHandle();
        ChatRunQueueLease lease = lease(runId, request());
        MapAccessor.runningRuns(service).put(runId, new ChatRunExecutionService.RunningChatRun(lease, runningRun));
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "PROMPT"));
        when(traceService.cancelRun(runId, createdAt)).thenReturn(false);

        service.cancel(runId);

        assertFalse(runningRun.isCancellationRequested());
        assertTrue(MapAccessor.runningRuns(service).containsKey(runId));
        verify(queueRepository, never()).deletePendingQueueEntry(runId);
        verify(queueRepository, never()).deleteQueueEntryIfOwned(any());
    }

    @Test
    void cancellationDuringExecutionDoesNotMarkRunFailed() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunExecutionService service = service(
            executor,
            queueRepository,
            chatExecutionService,
            traceService,
            queryService
        );
        String runId = UUID.randomUUID().toString();
        ChatRunQueueLease lease = lease(runId, request());
        when(queueRepository.claimNext(anyString(), any(Instant.class), any(Duration.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(queryService.getTrace(runId)).thenReturn(
            trace(runId, "RECEIVED"),
            trace(runId, "CANCELLED")
        );
        when(chatExecutionService.executeWithTraceContext(
            eq(lease.request()),
            any(ChatRunTraceService.RunTraceContext.class),
            any(ChatCancellationToken.class)
        )).thenThrow(new ChatRunCancelledException());

        service.requestProcessing();
        executor.runNext();

        verify(traceService, never()).failRun(any(), any(), any());
        verify(queueRepository).deleteQueueEntryIfOwned(lease);
    }

    @Test
    void heartbeatExtendsLeaseDuringExecution() {
        ManualExecutorService executor = new ManualExecutorService();
        ManualScheduledExecutorService heartbeatExecutor = new ManualScheduledExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunQueueLease lease = lease(UUID.randomUUID().toString(), request());
        ChatRunExecutionService service = service(
            executor,
            heartbeatExecutor,
            queueRepository,
            chatExecutionService,
            mock(ChatRunTraceService.class),
            queryService
        );
        when(queueRepository.claimNext(anyString(), any(Instant.class), any(Duration.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(queryService.getTrace(lease.runId())).thenReturn(trace(lease.runId(), "RECEIVED"));
        when(queueRepository.extendLease(eq(lease), any(Instant.class), any(Instant.class))).thenReturn(true);
        when(chatExecutionService.executeWithTraceContext(
            eq(lease.request()),
            any(ChatRunTraceService.RunTraceContext.class),
            any(ChatCancellationToken.class)
        )).thenAnswer(invocation -> {
            heartbeatExecutor.runNextScheduled();
            return null;
        });

        service.requestProcessing();
        executor.runNext();

        verify(queueRepository).extendLease(eq(lease), any(Instant.class), any(Instant.class));
        verify(queueRepository).deleteQueueEntryIfOwned(lease);
    }

    @Test
    void lostLeaseCancelsLocalExecutionAndDoesNotFailOrDeleteOwnedRow() {
        ManualExecutorService executor = new ManualExecutorService();
        ManualScheduledExecutorService heartbeatExecutor = new ManualScheduledExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunQueueLease lease = lease(UUID.randomUUID().toString(), request());
        ChatRunExecutionService service = service(
            executor,
            heartbeatExecutor,
            queueRepository,
            chatExecutionService,
            traceService,
            queryService
        );
        when(queueRepository.claimNext(anyString(), any(Instant.class), any(Duration.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(queryService.getTrace(lease.runId())).thenReturn(trace(lease.runId(), "RECEIVED"));
        when(queueRepository.extendLease(eq(lease), any(Instant.class), any(Instant.class))).thenReturn(false);
        when(chatExecutionService.executeWithTraceContext(
            eq(lease.request()),
            any(ChatRunTraceService.RunTraceContext.class),
            any(ChatCancellationToken.class)
        )).thenAnswer(invocation -> {
            ChatCancellationToken token = invocation.getArgument(2);
            heartbeatExecutor.runNextScheduled();
            assertTrue(token.isCancellationRequested());
            token.throwIfCancellationRequested();
            return null;
        });

        service.requestProcessing();
        executor.runNext();

        verify(traceService, never()).failRun(any(), any(), any());
        verify(queueRepository, never()).deleteQueueEntryIfOwned(any());
        verify(queueRepository, never()).deletePendingQueueEntry(lease.runId());
        verify(traceService).insertEvent(any(ChatRunTraceService.RunTraceContext.class), eq("LEASE_LOST"), any());
    }

    @Test
    void terminalRunAfterLeaseGuardRejectionDeletesOwnedQueueRow() {
        ManualExecutorService executor = new ManualExecutorService();
        ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
        ChatExecutionService chatExecutionService = mock(ChatExecutionService.class);
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatRunQueueLease lease = lease(UUID.randomUUID().toString(), request());
        ChatRunExecutionService service = service(
            executor,
            queueRepository,
            chatExecutionService,
            traceService,
            queryService
        );
        when(queueRepository.claimNext(anyString(), any(Instant.class), any(Duration.class))).thenReturn(
            Optional.of(lease),
            Optional.<ChatRunQueueLease>empty()
        );
        when(queueRepository.hasPendingRuns()).thenReturn(false);
        when(queryService.getTrace(lease.runId())).thenReturn(
            trace(lease.runId(), "RECEIVED"),
            trace(lease.runId(), "CANCELLED")
        );
        when(chatExecutionService.executeWithTraceContext(
            eq(lease.request()),
            any(ChatRunTraceService.RunTraceContext.class),
            any(ChatCancellationToken.class)
        )).thenThrow(new ChatRunLeaseLostException("cancelled remotely"));

        service.requestProcessing();
        executor.runNext();

        verify(traceService, never()).failRun(any(), any(), any());
        verify(queueRepository).deleteQueueEntryIfOwned(lease);
    }

    private ChatRunExecutionService service(
        ManualExecutorService executor,
        ChatRunQueueRepository queueRepository
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
        ChatRunQueueRepository queueRepository,
        ChatExecutionService chatExecutionService,
        ChatRunTraceService traceService,
        ChatRunQueryService queryService
    ) {
        ChatExecutionProperties properties = new ChatExecutionProperties();
        properties.setThreads(1);
        properties.setClaimLeaseSeconds(300);
        properties.setHeartbeatIntervalMillis(1000);
        return new ChatRunExecutionService(
            executor,
            new ManualScheduledExecutorService(),
            properties,
            queueRepository,
            chatExecutionService,
            traceService,
            queryService
        );
    }

    private ChatRunExecutionService service(
        AbstractExecutorService executor,
        ManualScheduledExecutorService heartbeatExecutor,
        ChatRunQueueRepository queueRepository,
        ChatExecutionService chatExecutionService,
        ChatRunTraceService traceService,
        ChatRunQueryService queryService
    ) {
        ChatExecutionProperties properties = new ChatExecutionProperties();
        properties.setThreads(1);
        properties.setClaimLeaseSeconds(300);
        properties.setHeartbeatIntervalMillis(1000);
        return new ChatRunExecutionService(
            executor,
            heartbeatExecutor,
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
            1,
            "worker-1",
            Instant.parse("2026-04-19T00:05:01Z")
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

    private ChatRunStatusResponse status(
        String runId,
        String status,
        String failureCode,
        String failureMessage
    ) {
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        return new ChatRunStatusResponse(
            runId,
            status,
            createdAt,
            "COMPLETED".equals(status) ? Instant.parse("2026-04-19T00:00:02Z") : null,
            "FAILED".equals(status) ? Instant.parse("2026-04-19T00:00:02Z") : null,
            null,
            "FAILED".equals(status) ? "LLM" : null,
            failureCode,
            failureMessage
        );
    }

    private ChatExecutionResponse response(String runId) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Hello",
            "Hi",
            null,
            "2026-04-19T00:00:02Z",
            1,
            1,
            2,
            null,
            List.of(),
            List.of(),
            KnowledgeScopeResolved.empty(),
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            List.of(),
            runId
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

    private static class ManualScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {
        private final List<Runnable> scheduledTasks = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pendingTasks = List.copyOf(scheduledTasks);
            scheduledTasks.clear();
            return pendingTasks;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && scheduledTasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            scheduledTasks.add(command);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduledTasks.add(command);
            return new ManualScheduledFuture();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("Callable scheduling is not needed in this test");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            scheduledTasks.add(command);
            return new ManualScheduledFuture();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduledTasks.add(command);
            return new ManualScheduledFuture();
        }

        void runNextScheduled() {
            scheduledTasks.getFirst().run();
        }
    }

    private static class ManualScheduledFuture implements ScheduledFuture<Object> {
        private boolean cancelled;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private static class MapAccessor {
        @SuppressWarnings("unchecked")
        static java.util.Map<String, ChatRunExecutionService.RunningChatRun> runningRuns(ChatRunExecutionService service) {
            return (java.util.Map<String, ChatRunExecutionService.RunningChatRun>) ReflectionTestUtils.getField(service, "runningRuns");
        }
    }

    private ChatRunQueryService serviceQueryService(ChatRunExecutionService service) {
        return (ChatRunQueryService) ReflectionTestUtils.getField(service, "chatRunQueryService");
    }
}
