package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatRunExecutionService {

    private final ExecutorService chatExecutionExecutor;
    private final ChatExecutionService chatExecutionService;
    private final ChatRunTraceService chatRunTraceService;
    private final ChatRunQueryService chatRunQueryService;
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public ChatRunExecutionService(
        @Qualifier("chatExecutionExecutor") ExecutorService chatExecutionExecutor,
        ChatExecutionService chatExecutionService,
        ChatRunTraceService chatRunTraceService,
        ChatRunQueryService chatRunQueryService
    ) {
        this.chatExecutionExecutor = chatExecutionExecutor;
        this.chatExecutionService = chatExecutionService;
        this.chatRunTraceService = chatRunTraceService;
        this.chatRunQueryService = chatRunQueryService;
    }

    public ChatRunSubmissionResponse submit(ChatExecutionRequest request) {
        validateRequest(request);
        ChatMode mode = request.mode() == null ? ChatMode.DIRECT : request.mode();
        ChatRunTraceService.RunTraceContext context = chatRunTraceService.startRun(request, mode);
        FutureTask<Void> task = new FutureTask<>(() -> {
            execute(context, request);
            return null;
        });
        runningTasks.put(context.id(), task);
        try {
            chatExecutionExecutor.execute(task);
        } catch (RejectedExecutionException exception) {
            runningTasks.remove(context.id());
            ApiException apiException = new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "chat_run.queue_full",
                "Chat execution queue is full",
                exception
            );
            chatRunTraceService.failRun(context, "QUEUE", apiException);
            throw apiException;
        }

        return new ChatRunSubmissionResponse(
            context.id(),
            "RECEIVED",
            context.createdAt(),
            "/api/chat-runs/" + context.id() + "/trace",
            "/api/chat-runs/" + context.id() + "/result"
        );
    }

    public ChatExecutionResponse getResult(String runId) {
        return chatRunQueryService.getResult(runId);
    }

    public ChatRunTraceDetail cancel(String runId) {
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(runId);
        Future<?> future = runningTasks.remove(trace.id());
        if (future != null) {
            future.cancel(true);
        }
        chatRunTraceService.cancelRun(trace.id(), trace.createdAt());
        return chatRunQueryService.getTrace(trace.id());
    }

    private void execute(ChatRunTraceService.RunTraceContext context, ChatExecutionRequest request) {
        try {
            if (!Thread.currentThread().isInterrupted()) {
                chatExecutionService.executeWithTraceContext(request, context);
            }
        } finally {
            runningTasks.remove(context.id());
        }
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
