package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.service.audit.ChatRunQueueLease;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.cancellation.ChatCancellationHandle;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.example.demo.service.cancellation.ChatRunCancelledException;
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
        verify(queueRepository).deleteQueueEntry(runId);
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
        MapAccessor.runningRuns(service).put(runId, runningRun);
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "PROMPT"));
        when(traceService.cancelRun(runId, createdAt)).thenAnswer(invocation -> {
            assertFalse(runningRun.isCancellationRequested());
            return true;
        });

        service.cancel(runId);

        assertTrue(runningRun.isCancellationRequested());
        verify(queueRepository).deleteQueueEntry(runId);
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
        MapAccessor.runningRuns(service).put(runId, runningRun);
        when(queryService.getTrace(runId)).thenReturn(trace(runId, "PROMPT"));
        when(traceService.cancelRun(runId, createdAt)).thenReturn(false);

        service.cancel(runId);

        assertFalse(runningRun.isCancellationRequested());
        assertTrue(MapAccessor.runningRuns(service).containsKey(runId));
        verify(queueRepository, never()).deleteQueueEntry(runId);
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
        verify(queueRepository).deleteQueueEntry(runId);
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
        verify(queueRepository, never()).deleteQueueEntry(lease.runId());
        verify(traceService).insertEvent(any(ChatRunTraceService.RunTraceContext.class), eq("LEASE_LOST"), any());
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
        static java.util.Map<String, ChatCancellationHandle> runningRuns(ChatRunExecutionService service) {
            return (java.util.Map<String, ChatCancellationHandle>) ReflectionTestUtils.getField(service, "runningRuns");
        }
    }

    private ChatRunQueryService serviceQueryService(ChatRunExecutionService service) {
        return (ChatRunQueryService) ReflectionTestUtils.getField(service, "chatRunQueryService");
    }
}
