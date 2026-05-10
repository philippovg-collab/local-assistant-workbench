package com.example.demo.service;

import com.example.demo.error.ErrorReasonResolver;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class ChatRunStateMachine {

    private final ChatRunTraceRepository repository;

    public ChatRunStateMachine(ChatRunTraceRepository repository) {
        this.repository = repository;
    }

    public boolean transitionStage(ChatRunTraceService.RunTraceContext context, String status) {
        if (context == null || status == null || status.isBlank()) {
            return false;
        }
        return repository.transitionStage(context.id(), status, context.leaseToken());
    }

    public boolean complete(
        ChatRunTraceService.RunTraceContext context,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus
    ) {
        if (context == null) {
            return false;
        }
        Instant completedAt = Instant.now();
        boolean completed = repository.completeRun(
            context.id(),
            resolvedModel,
            appliedAnswerMode,
            contextStatus,
            completedAt,
            Duration.between(context.createdAt(), completedAt).toMillis(),
            context.leaseToken()
        );
        if (completed) {
            repository.insertEvent(context.id(), "COMPLETED", Map.of(), completedAt);
        }
        return completed;
    }

    public boolean completeWithResult(
        ChatRunTraceService.RunTraceContext context,
        ChatExecutionResponse response
    ) {
        if (context == null) {
            return false;
        }
        Instant completedAt = Instant.now();
        return repository.completeRunWithResult(
            context.id(),
            response == null ? null : response.model(),
            response == null ? null : response.answerModeApplied(),
            response == null ? null : response.contextStatus(),
            completedAt,
            Duration.between(context.createdAt(), completedAt).toMillis(),
            response,
            context.leaseToken()
        );
    }

    public boolean fail(
        ChatRunTraceService.RunTraceContext context,
        String failureStage,
        Throwable throwable
    ) {
        if (context == null) {
            return false;
        }
        Instant failedAt = Instant.now();
        String failureCode = reasonCode(throwable);
        String failureMessage = rootMessage(throwable);
        boolean failed = repository.failRun(
            context.id(),
            failureStage,
            failureCode,
            failureMessage,
            failedAt,
            Duration.between(context.createdAt(), failedAt).toMillis(),
            context.leaseToken()
        );
        if (failed) {
            repository.insertEvent(context.id(), "FAILED", Map.of(
                "stage",
                failureStage,
                "code",
                failureCode,
                "message",
                failureMessage
            ), failedAt);
        }
        return failed;
    }

    public boolean cancel(String runId, Instant createdAt) {
        Instant cancelledAt = Instant.now();
        boolean cancelled = repository.cancelRun(
            runId,
            cancelledAt,
            createdAt == null ? 0 : Duration.between(createdAt, cancelledAt).toMillis()
        );
        if (cancelled) {
            repository.insertEvent(runId, "CANCELLED", Map.of(), cancelledAt);
        }
        return cancelled;
    }

    private String reasonCode(Throwable throwable) {
        return ErrorReasonResolver.reasonCode(throwable, "chat_trace.execution_failed");
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
