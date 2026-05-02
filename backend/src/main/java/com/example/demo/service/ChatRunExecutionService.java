package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.service.audit.ChatRunQueueLease;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.cancellation.ChatCancellationHandle;
import com.example.demo.service.cancellation.ChatRunCancelledException;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ChatRunExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatRunExecutionService.class);

    private final ExecutorService chatExecutionExecutor;
    private final ScheduledExecutorService leaseHeartbeatExecutor;
    private final ChatExecutionProperties properties;
    private final ChatRunQueueRepository queueRepository;
    private final ChatExecutionService chatExecutionService;
    private final ChatRunTraceService chatRunTraceService;
    private final ChatRunQueryService chatRunQueryService;
    private final String workerId;
    private final Map<String, ChatCancellationHandle> runningRuns = new ConcurrentHashMap<>();
    private final AtomicInteger scheduledWorkers = new AtomicInteger(0);

    public ChatRunExecutionService(
        @Qualifier("chatExecutionExecutor") ExecutorService chatExecutionExecutor,
        @Qualifier("chatLeaseHeartbeatExecutor") ScheduledExecutorService leaseHeartbeatExecutor,
        ChatExecutionProperties properties,
        ChatRunQueueRepository queueRepository,
        ChatExecutionService chatExecutionService,
        ChatRunTraceService chatRunTraceService,
        ChatRunQueryService chatRunQueryService
    ) {
        this.chatExecutionExecutor = chatExecutionExecutor;
        this.leaseHeartbeatExecutor = leaseHeartbeatExecutor;
        this.properties = properties;
        this.queueRepository = queueRepository;
        this.chatExecutionService = chatExecutionService;
        this.chatRunTraceService = chatRunTraceService;
        this.chatRunQueryService = chatRunQueryService;
        this.workerId = buildWorkerId();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public ChatRunSubmissionResponse submit(ChatExecutionRequest request) {
        validateRequest(request);
        ChatMode mode = request.mode() == null ? ChatMode.DIRECT : request.mode();
        EnqueuedChatRun run = queueRepository.enqueue(request, mode, Instant.now());
        requestProcessing();

        return new ChatRunSubmissionResponse(
            run.runId(),
            "RECEIVED",
            run.createdAt(),
            "/api/chat-runs/" + run.runId() + "/status",
            "/api/chat-runs/" + run.runId() + "/trace",
            "/api/chat-runs/" + run.runId() + "/result"
        );
    }

    public ChatRunTraceDetail cancel(String runId) {
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(runId);
        if (chatRunTraceService.cancelRun(trace.id(), trace.createdAt())) {
            queueRepository.deleteQueueEntry(trace.id());
            ChatCancellationHandle runningRun = runningRuns.get(trace.id());
            if (runningRun != null) {
                runningRun.requestCancellation();
            }
        }
        return chatRunQueryService.getTrace(trace.id());
    }

    public void requestProcessing() {
        int maxWorkers = Math.max(1, properties.getThreads());
        while (true) {
            int currentWorkers = scheduledWorkers.get();
            if (currentWorkers >= maxWorkers) {
                return;
            }
            if (!scheduledWorkers.compareAndSet(currentWorkers, currentWorkers + 1)) {
                continue;
            }
            try {
                chatExecutionExecutor.execute(this::drainQueue);
            } catch (RejectedExecutionException exception) {
                scheduledWorkers.decrementAndGet();
                logger.warn("Chat execution executor rejected queue processing request; durable runs remain pending", exception);
                return;
            }
        }
    }

    private void drainQueue() {
        try {
            Instant now = Instant.now();
            queueRepository.recoverExpiredLeases(now, properties.getMaxAttempts());
            while (!Thread.currentThread().isInterrupted()) {
                ChatRunQueueLease lease = queueRepository.claimNext(
                    workerId,
                    Instant.now(),
                    Duration.ofSeconds(Math.max(1, properties.getClaimLeaseSeconds()))
                ).orElse(null);
                if (lease == null) {
                    break;
                }
                executeLease(lease);
            }
        } finally {
            scheduledWorkers.decrementAndGet();
            if (queueRepository.hasPendingRuns()) {
                requestProcessing();
            }
        }
    }

    private void executeLease(ChatRunQueueLease lease) {
        ChatRunTraceService.RunTraceContext context = new ChatRunTraceService.RunTraceContext(
            lease.runId(),
            lease.createdAt(),
            lease.leaseToken()
        );
        ChatCancellationHandle cancellationHandle = new ChatCancellationHandle();
        runningRuns.put(lease.runId(), cancellationHandle);
        LeaseHeartbeat heartbeat = null;
        try {
            if (isTerminalRun(lease.runId())) {
                deleteQueueEntryIfTerminal(lease.runId());
                return;
            }
            heartbeat = startHeartbeat(lease, context, cancellationHandle);
            cancellationHandle.throwIfCancellationRequested();
            chatExecutionService.executeWithTraceContext(lease.request(), context, cancellationHandle);
            if (!heartbeat.leaseLost()) {
                heartbeat.close();
                queueRepository.deleteQueueEntryIfOwned(lease);
            }
        } catch (ChatRunLeaseLostException exception) {
            recordLeaseLost(context, lease, exception);
        } catch (ChatRunCancelledException exception) {
            if (heartbeat == null || !heartbeat.leaseLost()) {
                deleteQueueEntryIfTerminal(lease.runId());
            }
        } catch (RuntimeException exception) {
            if (heartbeat != null && heartbeat.leaseLost()) {
                logger.warn("Durable chat run stopped after lease loss: runId={}", lease.runId(), exception);
                return;
            }
            try {
                boolean failed = chatRunTraceService.failRun(context, "EXECUTION", exception);
                if (failed) {
                    queueRepository.deleteQueueEntryIfOwned(lease);
                } else {
                    deleteQueueEntryIfTerminal(lease.runId());
                }
            } catch (RuntimeException traceException) {
                logger.warn("Unable to mark durable chat run failed: runId={}", lease.runId(), traceException);
            }
            logger.warn("Durable chat run failed: runId={}", lease.runId(), exception);
        } finally {
            if (heartbeat != null) {
                heartbeat.close();
            }
            runningRuns.remove(lease.runId(), cancellationHandle);
        }
    }

    private LeaseHeartbeat startHeartbeat(
        ChatRunQueueLease lease,
        ChatRunTraceService.RunTraceContext context,
        ChatCancellationHandle cancellationHandle
    ) {
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        long intervalMillis = properties.getHeartbeatIntervalMillis();
        ScheduledFuture<?> future = leaseHeartbeatExecutor.scheduleWithFixedDelay(
            () -> heartbeatLease(lease, context, cancellationHandle, leaseLost),
            intervalMillis,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
        return new LeaseHeartbeat(future, leaseLost);
    }

    private void heartbeatLease(
        ChatRunQueueLease lease,
        ChatRunTraceService.RunTraceContext context,
        ChatCancellationHandle cancellationHandle,
        AtomicBoolean leaseLost
    ) {
        try {
            Instant heartbeatAt = Instant.now();
            Instant leaseExpiresAt = heartbeatAt.plusSeconds(Math.max(1, properties.getClaimLeaseSeconds()));
            boolean extended = queueRepository.extendLease(lease, heartbeatAt, leaseExpiresAt);
            if (!extended && leaseLost.compareAndSet(false, true)) {
                cancellationHandle.requestCancellation();
                chatRunTraceService.insertEvent(context, "LEASE_LOST", Map.of(
                    "attemptCount",
                    lease.attemptCount(),
                    "leaseOwner",
                    lease.leaseOwner()
                ));
            }
        } catch (RuntimeException exception) {
            if (leaseLost.compareAndSet(false, true)) {
                cancellationHandle.requestCancellation();
                chatRunTraceService.insertEvent(context, "LEASE_LOST", Map.of(
                    "attemptCount",
                    lease.attemptCount(),
                    "leaseOwner",
                    lease.leaseOwner(),
                    "reason",
                    exception.getClass().getSimpleName()
                ));
            }
            logger.warn("Unable to heartbeat durable chat run lease: runId={}", lease.runId(), exception);
        }
    }

    private void recordLeaseLost(
        ChatRunTraceService.RunTraceContext context,
        ChatRunQueueLease lease,
        RuntimeException exception
    ) {
        chatRunTraceService.insertEvent(context, "LEASE_LOST", Map.of(
            "attemptCount",
            lease.attemptCount(),
            "leaseOwner",
            lease.leaseOwner()
        ));
        logger.warn("Durable chat run lease lost: runId={}", lease.runId(), exception);
    }

    private void deleteQueueEntryIfTerminal(String runId) {
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(runId);
        if (trace != null && isTerminal(trace.status())) {
            queueRepository.deleteQueueEntry(runId);
        }
    }

    private boolean isTerminalRun(String runId) {
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(runId);
        return trace != null && isTerminal(trace.status());
    }

    private boolean isTerminal(String status) {
        return "FAILED".equals(status) || "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }

    private String buildWorkerId() {
        return ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
    }

    private void validateRequest(ChatExecutionRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_request",
                "Field 'prompt' is required"
            );
        }
    }

    private record LeaseHeartbeat(ScheduledFuture<?> future, AtomicBoolean leaseLostFlag) implements AutoCloseable {

        public boolean leaseLost() {
            return leaseLostFlag.get();
        }

        @Override
        public void close() {
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
