package com.example.demo.service;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.CodedException;
import com.example.demo.error.ErrorType;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.example.demo.service.cancellation.ChatRunCancelledException;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatResultRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ChatResultRecorder.class);

    private final ChatRunTraceService chatRunTraceService;
    private final ChatAuditProperties chatAuditProperties;
    private final boolean exposeStartedTraceIds;

    public ChatResultRecorder(ChatRunTraceService chatRunTraceService, ChatAuditProperties chatAuditProperties) {
        this.chatRunTraceService = chatRunTraceService == null ? ChatRunTraceService.noop() : chatRunTraceService;
        this.chatAuditProperties = chatAuditProperties;
        this.exposeStartedTraceIds = chatRunTraceService != null;
    }

    public boolean exposeStartedTraceIds() {
        return exposeStartedTraceIds;
    }

    public ChatExecutionContext startTraceContext(
        ChatExecutionRequest request,
        ChatMode mode,
        ChatCancellationToken cancellationToken,
        boolean exposeTraceId
    ) {
        ChatCancellationToken effectiveToken = effectiveCancellationToken(cancellationToken);
        try {
            return new ChatExecutionContext(
                chatRunTraceService,
                chatRunTraceService.startRun(request, mode),
                effectiveToken,
                exposeTraceId
            );
        } catch (RuntimeException exception) {
            handleTraceStorageFailure(null, exception, false);
            ChatRunTraceService noopTraceService = ChatRunTraceService.noop();
            return new ChatExecutionContext(
                noopTraceService,
                noopTraceService.startRun(request, mode),
                effectiveToken,
                false
            );
        }
    }

    public ChatExecutionContext withTraceContext(
        ChatRunTraceService.RunTraceContext traceContext,
        ChatCancellationToken cancellationToken
    ) {
        ChatRunTraceService effectiveTraceService = traceContext == null
            ? ChatRunTraceService.noop()
            : chatRunTraceService;
        ChatRunTraceService.RunTraceContext effectiveTraceContext = traceContext == null
            ? effectiveTraceService.startRun(null, ChatMode.DIRECT)
            : traceContext;
        return new ChatExecutionContext(
            effectiveTraceService,
            effectiveTraceContext,
            effectiveCancellationToken(cancellationToken),
            traceContext != null
        );
    }

    public void saveRequestSnapshot(
        ChatExecutionContext context,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest
    ) {
        traceStage(context, () -> context.traceService().saveRequestSnapshot(
            context.traceContext(),
            request,
            normalizedRequest
        ));
    }

    public void savePromptSnapshot(
        ChatExecutionContext context,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved resolvedScope
    ) {
        traceStage(context, () -> context.traceService().savePromptSnapshot(
            context.traceContext(),
            promptPolicy.snapshot(),
            instructionTrace,
            resolvedScope
        ));
    }

    public void saveRetrievalSummary(
        ChatExecutionContext context,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug
    ) {
        traceStage(context, () -> context.traceService().saveRetrievalSummary(
            context.traceContext(),
            retrievalStatus,
            trace,
            debug
        ));
    }

    public void savePromptMessages(ChatExecutionContext context, List<LlmClient.Message> messages) {
        traceStage(context, () -> context.traceService().savePromptMessages(context.traceContext(), messages));
    }

    public void saveLlmSuccess(
        ChatExecutionContext context,
        LlmClient.ChatRequest chatRequest,
        LlmClient.ChatResult result
    ) {
        traceStage(context, () -> context.traceService().saveLlmSuccess(
            context.traceContext(),
            chatRequest,
            result,
            null
        ));
    }

    public void saveLlmFailure(
        ChatExecutionContext context,
        LlmClient.ChatRequest chatRequest,
        RuntimeException exception,
        long latencyMs
    ) {
        traceStage(context, () -> context.traceService().saveLlmFailure(
            context.traceContext(),
            chatRequest,
            exception,
            latencyMs,
            null
        ));
    }

    public void transitionStage(ChatExecutionContext context, String status) {
        if (context == null) {
            return;
        }
        try {
            boolean transitioned = context.traceService().transitionStage(context.traceContext(), status);
            if (!transitioned && isLeaseGuarded(context)) {
                throw new ChatRunLeaseLostException("Durable chat run lease no longer owns run " + context.traceContext().id());
            }
        } catch (ChatRunCancelledException | ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            handleTraceStorageFailure(context, exception, false);
        }
    }

    public ChatExecutionResponse complete(
        ChatExecutionContext context,
        ChatResultFactory.PreparedChatResult preparedResult
    ) {
        context.throwIfCancellationRequested();
        ChatExecutionResponse responseWithTraceId = withAuditRunId(preparedResult.response(), context.auditRunId());
        traceStage(context, () -> context.traceService().saveOutput(
            context.traceContext(),
            preparedResult.rawModelAnswer(),
            preparedResult.finalUserAnswer(),
            preparedResult.sources(),
            preparedResult.postprocess(),
            preparedResult.abstained(),
            preparedResult.strictSourcesBlockedAnswer()
        ));
        transitionStage(context, "POSTPROCESSED");
        context.throwIfCancellationRequested();
        completeTrace(context, responseWithTraceId);
        return responseWithTraceId;
    }

    public void failTrace(ChatExecutionContext context, String failureStage, RuntimeException exception) {
        if (context == null) {
            return;
        }
        try {
            context.traceService().failRun(context.traceContext(), failureStage, exception);
        } catch (RuntimeException traceException) {
            handleTraceStorageFailure(context, traceException, true);
        }
    }

    private void completeTrace(ChatExecutionContext context, ChatExecutionResponse response) {
        if (context == null) {
            return;
        }
        try {
            boolean completed = context.traceService().completeRunWithResult(context.traceContext(), response);
            if (!completed && isLeaseGuarded(context)) {
                throw new ChatRunLeaseLostException("Durable chat run lease cannot complete run " + context.traceContext().id());
            }
        } catch (ChatRunCancelledException | ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            handleTraceStorageFailure(context, exception, true);
        }
    }

    private void traceStage(ChatExecutionContext context, Runnable operation) {
        if (context == null || operation == null) {
            return;
        }
        try {
            operation.run();
        } catch (ChatRunCancelledException | ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            handleTraceStorageFailure(context, exception, false);
        }
    }

    private void handleTraceStorageFailure(
        ChatExecutionContext context,
        RuntimeException exception,
        boolean terminalOperation
    ) {
        if ((terminalOperation && isLeaseGuarded(context)) || failClosed()) {
            if (exception instanceof CodedException codedException) {
                throw codedException;
            }
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "chat_trace.storage_failed",
                "Chat trace recording failed and audit fail-closed mode is enabled.",
                exception
            );
        }
        logger.warn("Chat trace recording failed; continuing because audit fail-closed mode is disabled", exception);
    }

    private boolean failClosed() {
        return chatAuditProperties != null && chatAuditProperties.isFailClosed();
    }

    private boolean isLeaseGuarded(ChatExecutionContext context) {
        return context != null
            && context.traceContext() != null
            && context.traceContext().leaseToken() != null;
    }

    private ChatExecutionResponse withAuditRunId(ChatExecutionResponse response, String auditRunId) {
        return new ChatExecutionResponse(
            response.mode(),
            response.model(),
            response.prompt(),
            response.answer(),
            response.contextStatus(),
            response.createdAt(),
            response.promptTokens(),
            response.completionTokens(),
            response.totalTokens(),
            response.answerModeApplied(),
            response.appliedInstructions(),
            response.instructionTrace(),
            response.knowledgeScopeResolved(),
            response.retrievalTrace(),
            response.retrievalDebug(),
            response.sources(),
            auditRunId,
            response.conversationId(),
            response.turnNo(),
            response.contextAssemblyId(),
            response.contextSummary()
        );
    }

    private ChatCancellationToken effectiveCancellationToken(ChatCancellationToken cancellationToken) {
        return cancellationToken == null ? ChatCancellationToken.none() : cancellationToken;
    }

    public record ChatExecutionContext(
        ChatRunTraceService traceService,
        ChatRunTraceService.RunTraceContext traceContext,
        ChatCancellationToken cancellationToken,
        boolean exposeTraceId
    ) {
        void throwIfCancellationRequested() {
            cancellationToken.throwIfCancellationRequested();
        }

        String auditRunId() {
            return exposeTraceId ? traceContext.id() : null;
        }
    }
}
