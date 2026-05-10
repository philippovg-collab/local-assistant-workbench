package com.example.demo.service;

import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.service.ChatPromptAssemblyService.PromptAssembly;
import com.example.demo.service.ChatPromptAssemblyService.ResolvedChatRequest;
import com.example.demo.service.ChatResultRecorder.ChatExecutionContext;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.example.demo.service.cancellation.ChatRunCancelledException;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import com.example.demo.service.context.ConversationStickyStateService;
import com.example.demo.service.context.ConversationStickyStateService.StickyResolution;
import com.example.demo.service.context.ConversationSummaryTriggerService;
import com.example.demo.service.context.PreparedContextAssembly;
import com.example.demo.service.context.RetrievalQueryResolutionService;
import com.example.demo.service.memory.MemoryCandidateExtractionTriggerService;
import com.example.demo.validation.InputLimits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatExecutionService {

    private final ChatPromptAssemblyService promptAssemblyService;
    private final ChatResultRecorder resultRecorder;
    private final ChatContextAssemblyCoordinator contextAssemblyCoordinator;
    private final ConversationStickyStateService stickyStateService;
    private final ChatExecutionPipeline executionPipeline;

    @Autowired
    public ChatExecutionService(
        ChatPromptAssemblyService promptAssemblyService,
        ChatRetrievalStep retrievalStep,
        ChatLlmExecutionStep llmExecutionStep,
        ChatResultFactory resultFactory,
        ChatResultRecorder resultRecorder,
        ChatContextAssemblyCoordinator contextAssemblyCoordinator,
        ChatRagNoContextPolicy ragNoContextPolicy,
        ConversationStickyStateService stickyStateService,
        RetrievalQueryResolutionService retrievalQueryResolutionService,
        ConversationSummaryTriggerService summaryTriggerService,
        MemoryCandidateExtractionTriggerService memoryTriggerService
    ) {
        this.promptAssemblyService = promptAssemblyService;
        this.resultRecorder = resultRecorder;
        this.contextAssemblyCoordinator = contextAssemblyCoordinator == null
            ? new ChatContextAssemblyCoordinator()
            : contextAssemblyCoordinator;
        this.stickyStateService = stickyStateService;
        this.executionPipeline = new ChatExecutionPipeline(
            promptAssemblyService,
            retrievalStep,
            llmExecutionStep,
            resultFactory,
            resultRecorder,
            this.contextAssemblyCoordinator,
            ragNoContextPolicy == null ? new ChatRagNoContextPolicy() : ragNoContextPolicy,
            stickyStateService,
            retrievalQueryResolutionService,
            summaryTriggerService,
            memoryTriggerService
        );
    }

    public ChatExecutionService(
        ChatPromptAssemblyService promptAssemblyService,
        ChatRetrievalStep retrievalStep,
        ChatLlmExecutionStep llmExecutionStep,
        ChatResultFactory resultFactory,
        ChatResultRecorder resultRecorder,
        ChatContextAssemblyCoordinator contextAssemblyCoordinator,
        ChatRagNoContextPolicy ragNoContextPolicy,
        ConversationStickyStateService stickyStateService,
        RetrievalQueryResolutionService retrievalQueryResolutionService
    ) {
        this(
            promptAssemblyService,
            retrievalStep,
            llmExecutionStep,
            resultFactory,
            resultRecorder,
            contextAssemblyCoordinator,
            ragNoContextPolicy,
            stickyStateService,
            retrievalQueryResolutionService,
            null,
            null
        );
    }

    public ChatExecutionService(
        ChatPromptAssemblyService promptAssemblyService,
        ChatRetrievalStep retrievalStep,
        ChatLlmExecutionStep llmExecutionStep,
        ChatResultFactory resultFactory,
        ChatResultRecorder resultRecorder,
        ChatContextAssemblyCoordinator contextAssemblyCoordinator,
        ChatRagNoContextPolicy ragNoContextPolicy,
        ConversationStickyStateService stickyStateService
    ) {
        this(
            promptAssemblyService,
            retrievalStep,
            llmExecutionStep,
            resultFactory,
            resultRecorder,
            contextAssemblyCoordinator,
            ragNoContextPolicy,
            stickyStateService,
            null,
            null,
            null
        );
    }

    public ChatExecutionService(
        ChatPromptAssemblyService promptAssemblyService,
        ChatRetrievalStep retrievalStep,
        ChatLlmExecutionStep llmExecutionStep,
        ChatResultFactory resultFactory,
        ChatResultRecorder resultRecorder
    ) {
        this(
            promptAssemblyService,
            retrievalStep,
            llmExecutionStep,
            resultFactory,
            resultRecorder,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    ChatExecutionResponse execute(ChatExecutionRequest request) {
        validateRequest(request);
        ChatMode mode = modeOf(request);
        ChatExecutionContext context = resultRecorder.startTraceContext(
            request,
            mode,
            ChatCancellationToken.none(),
            resultRecorder.exposeStartedTraceIds()
        );
        return executeWithContext(request, mode, context);
    }

    public ChatExecutionResponse executeWithTraceContext(
        ChatExecutionRequest request,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        return executeWithTraceContext(request, traceContext, ChatCancellationToken.none());
    }

    public ChatExecutionResponse executeWithTraceContext(
        ChatExecutionRequest request,
        ChatRunTraceService.RunTraceContext traceContext,
        ChatCancellationToken cancellationToken
    ) {
        validateRequest(request);
        ChatMode mode = modeOf(request);
        ChatExecutionContext context = resultRecorder.withTraceContext(traceContext, cancellationToken);
        return executeWithContext(request, mode, context);
    }

    private ChatExecutionResponse executeWithContext(
        ChatExecutionRequest request,
        ChatMode mode,
        ChatExecutionContext context
    ) {
        StickyResolution stickyResolution = resolveStickyState(request, mode, context);
        PromptAssembly assembly = assemblePrompt(request, stickyResolution.request(), mode, context);
        PreparedContextAssembly contextAssembly = contextAssemblyCoordinator.prepare(assembly, mode, context, stickyResolution);
        return executionPipeline.execute(assembly, contextAssembly, context, stickyResolution, mode);
    }

    private StickyResolution resolveStickyState(
        ChatExecutionRequest request,
        ChatMode mode,
        ChatExecutionContext context
    ) {
        if (stickyStateService == null) {
            return StickyResolution.inactive(request, "service_unavailable");
        }
        try {
            return stickyStateService.resolve(request, mode, context.traceContext());
        } catch (RuntimeException exception) {
            resultRecorder.failTrace(context, "STICKY_STATE", exception);
            throw exception;
        }
    }

    private PromptAssembly assemblePrompt(
        ChatExecutionRequest originalRequest,
        ChatExecutionRequest effectiveRequest,
        ChatMode mode,
        ChatExecutionContext context
    ) {
        try {
            context.throwIfCancellationRequested();
            ResolvedChatRequest resolvedRequest = promptAssemblyService.resolveRequest(effectiveRequest, mode);
            resultRecorder.saveRequestSnapshot(
                context,
                originalRequest,
                resolvedRequest.normalizedRequest()
            );
            context.throwIfCancellationRequested();
            PromptAssembly assembly = promptAssemblyService.assemblePrompt(resolvedRequest);
            resultRecorder.savePromptSnapshot(
                context,
                assembly.promptPolicy(),
                assembly.instructionTrace(),
                assembly.scopeContext().resolvedScope()
            );
            resultRecorder.transitionStage(context, "PROMPT_RESOLVED");
            context.throwIfCancellationRequested();
            return assembly;
        } catch (ChatRunCancelledException | ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            resultRecorder.failTrace(context, "PROMPT", exception);
            throw exception;
        }
    }

    private ChatMode modeOf(ChatExecutionRequest request) {
        return request.mode() == null ? ChatMode.DIRECT : request.mode();
    }

    private void validateRequest(ChatExecutionRequest request) {
        InputLimits.validateChatRequest(request);
    }

}
