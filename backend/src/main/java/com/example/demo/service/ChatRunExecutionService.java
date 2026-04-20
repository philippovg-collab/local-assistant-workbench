package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.infrastructure.audit.ChatRunQueueLease;
import com.example.demo.infrastructure.audit.EnqueuedChatRun;
import com.example.demo.infrastructure.audit.PostgresChatRunQueueRepository;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
    private final ChatExecutionProperties properties;
    private final PostgresChatRunQueueRepository queueRepository;
    private final ChatExecutionService chatExecutionService;
    private final ChatRunTraceService chatRunTraceService;
    private final ChatRunQueryService chatRunQueryService;
    private final Map<String, Thread> runningThreads = new ConcurrentHashMap<>();
    private final AtomicInteger scheduledWorkers = new AtomicInteger(0);

    public ChatRunExecutionService(
        @Qualifier("chatExecutionExecutor") ExecutorService chatExecutionExecutor,
        ChatExecutionProperties properties,
        PostgresChatRunQueueRepository queueRepository,
        ChatExecutionService chatExecutionService,
        ChatRunTraceService chatRunTraceService,
        ChatRunQueryService chatRunQueryService
    ) {
        this.chatExecutionExecutor = chatExecutionExecutor;
        this.properties = properties;
        this.queueRepository = queueRepository;
        this.chatExecutionService = chatExecutionService;
        this.chatRunTraceService = chatRunTraceService;
        this.chatRunQueryService = chatRunQueryService;
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
            "/api/chat-runs/" + run.runId() + "/trace",
            "/api/chat-runs/" + run.runId() + "/result"
        );
    }

    public ChatRunTraceDetail cancel(String runId) {
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(runId);
        if (chatRunTraceService.cancelRun(trace.id(), trace.createdAt())) {
            queueRepository.deleteQueueEntry(trace.id());
            Thread runningThread = runningThreads.remove(trace.id());
            if (runningThread != null) {
                runningThread.interrupt();
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
            queueRepository.recoverStaleClaims(
                now.minusSeconds(Math.max(1, properties.getClaimLeaseSeconds())),
                now
            );
            while (!Thread.currentThread().isInterrupted()) {
                ChatRunQueueLease lease = queueRepository.claimNext(Instant.now()).orElse(null);
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
            lease.createdAt()
        );
        Thread currentThread = Thread.currentThread();
        runningThreads.put(lease.runId(), currentThread);
        try {
            if (!currentThread.isInterrupted()) {
                chatExecutionService.executeWithTraceContext(lease.request(), context);
            }
            deleteQueueEntryIfTerminal(lease.runId());
        } catch (RuntimeException exception) {
            try {
                boolean failed = chatRunTraceService.failRun(context, "EXECUTION", exception);
                if (failed) {
                    queueRepository.deleteQueueEntry(lease.runId());
                } else {
                    deleteQueueEntryIfTerminal(lease.runId());
                }
            } catch (RuntimeException traceException) {
                logger.warn("Unable to mark durable chat run failed: runId={}", lease.runId(), traceException);
            }
            logger.warn("Durable chat run failed: runId={}", lease.runId(), exception);
        } finally {
            runningThreads.remove(lease.runId(), currentThread);
        }
    }

    private void deleteQueueEntryIfTerminal(String runId) {
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(runId);
        if (isTerminal(trace.status())) {
            queueRepository.deleteQueueEntry(runId);
        }
    }

    private boolean isTerminal(String status) {
        return "FAILED".equals(status) || "COMPLETED".equals(status) || "CANCELLED".equals(status);
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
}
