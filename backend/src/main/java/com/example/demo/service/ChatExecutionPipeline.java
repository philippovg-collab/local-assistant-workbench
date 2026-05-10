package com.example.demo.service;

import com.example.demo.llm.LlmClient;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.model.RetrievalQueryResolutionDecision;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.ChatPromptAssemblyService.PromptAssembly;
import com.example.demo.service.ChatResultFactory.ChatExecutionRequestParts;
import com.example.demo.service.ChatResultRecorder.ChatExecutionContext;
import com.example.demo.service.cancellation.ChatRunCancelledException;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import com.example.demo.service.context.ConversationStickyStateService;
import com.example.demo.service.context.ConversationStickyStateService.StickyResolution;
import com.example.demo.service.context.ConversationSummaryTriggerService;
import com.example.demo.service.context.PreparedContextAssembly;
import com.example.demo.service.context.RetrievalQueryResolutionService;
import com.example.demo.service.memory.MemoryCandidateExtractionTriggerService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

class ChatExecutionPipeline {

    private final ChatPromptAssemblyService promptAssemblyService;
    private final ChatRetrievalStep retrievalStep;
    private final ChatLlmExecutionStep llmExecutionStep;
    private final ChatResultFactory resultFactory;
    private final ChatResultRecorder resultRecorder;
    private final ChatContextAssemblyCoordinator contextAssemblyCoordinator;
    private final ChatRagNoContextPolicy ragNoContextPolicy;
    private final ConversationStickyStateService stickyStateService;
    private final RetrievalQueryResolutionService retrievalQueryResolutionService;
    private final ConversationSummaryTriggerService summaryTriggerService;
    private final MemoryCandidateExtractionTriggerService memoryTriggerService;

    ChatExecutionPipeline(
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
        this.retrievalStep = retrievalStep;
        this.llmExecutionStep = llmExecutionStep;
        this.resultFactory = resultFactory;
        this.resultRecorder = resultRecorder;
        this.contextAssemblyCoordinator = contextAssemblyCoordinator;
        this.ragNoContextPolicy = ragNoContextPolicy;
        this.stickyStateService = stickyStateService;
        this.retrievalQueryResolutionService = retrievalQueryResolutionService;
        this.summaryTriggerService = summaryTriggerService;
        this.memoryTriggerService = memoryTriggerService;
    }

    ChatExecutionResponse execute(
        PromptAssembly assembly,
        PreparedContextAssembly contextAssembly,
        ChatExecutionContext context,
        StickyResolution stickyResolution,
        ChatMode mode
    ) {
        if (mode == ChatMode.RAG) {
            return executeRag(assembly, contextAssembly, context, stickyResolution);
        }
        return executeDirect(assembly, contextAssembly, context, stickyResolution);
    }

    private ChatExecutionResponse executeRag(
        PromptAssembly assembly,
        PreparedContextAssembly contextAssembly,
        ChatExecutionContext context,
        StickyResolution stickyResolution
    ) {
        RetrievalQueryResolution resolution = resolveRetrievalQuery(assembly, contextAssembly);
        contextAssembly = contextAssembly.withRetrievalQueryResolution(resolution);
        RetrievalExecution retrieval = retrieve(assembly, context, resolution);
        MaterialRetrievalResult retrievalResult = retrieval.result();
        contextAssembly = contextAssembly.withRetrievalQueryResolution(retrieval.resolution());
        context.throwIfCancellationRequested();

        AnswerMode answerMode = assembly.promptPolicy().answerMode();
        ChatExecutionRequestParts requestParts = requestParts(assembly);

        ChatRagNoContextPolicy.NoContextCompletion noContext = ragNoContextPolicy.evaluate(
            retrievalResult,
            answerMode
        );
        if (noContext != null) {
            return completeNoContext(
                ChatMode.RAG,
                assembly,
                contextAssemblyCoordinator.emptyMessages(contextAssembly, context).contextAssembly(),
                requestParts,
                retrievalResult,
                noContext.answer(),
                noContext.sources(),
                null,
                noContext.strictSourcesBlockedAnswer(),
                context,
                stickyResolution
            );
        }

        ChatContextAssemblyCoordinator.ContextMessages persistedContext = contextAssemblyCoordinator.ragMessages(
            promptAssemblyService,
            assembly,
            retrievalResult,
            contextAssembly,
            context
        );
        List<LlmClient.Message> messages = persistedContext.messages();
        contextAssembly = persistedContext.contextAssembly();
        resultRecorder.savePromptMessages(context, messages);
        LlmClient.ChatRequest chatRequest = new LlmClient.ChatRequest(assembly.promptPolicy().model(), messages);
        LlmClient.ChatResult result = executeLlm(chatRequest, context);
        return completeWithStickyUpdate(
            context,
            contextAssemblyCoordinator.withContext(
                resultFactory.rag(requestParts, assembly.promptPolicy(), retrievalResult, result),
                contextAssembly
            ),
            assembly,
            stickyResolution,
            contextAssembly
        );
    }

    private ChatExecutionResponse executeDirect(
        PromptAssembly assembly,
        PreparedContextAssembly contextAssembly,
        ChatExecutionContext context,
        StickyResolution stickyResolution
    ) {
        context.throwIfCancellationRequested();
        resultRecorder.saveRetrievalSummary(
            context,
            "NOT_APPLICABLE",
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null
        );
        resultRecorder.transitionStage(context, "RETRIEVAL_DONE");
        ChatContextAssemblyCoordinator.ContextMessages persistedContext = contextAssemblyCoordinator.directMessages(
            promptAssemblyService,
            assembly,
            contextAssembly,
            context
        );
        List<LlmClient.Message> messages = persistedContext.messages();
        contextAssembly = persistedContext.contextAssembly();
        resultRecorder.savePromptMessages(context, messages);
        LlmClient.ChatRequest chatRequest = new LlmClient.ChatRequest(assembly.promptPolicy().model(), messages);
        LlmClient.ChatResult result = executeLlm(chatRequest, context);
        return completeWithStickyUpdate(
            context,
            contextAssemblyCoordinator.withContext(
                resultFactory.direct(requestParts(assembly), assembly.promptPolicy(), result),
                contextAssembly
            ),
            assembly,
            stickyResolution,
            contextAssembly
        );
    }

    private RetrievalQueryResolution resolveRetrievalQuery(
        PromptAssembly assembly,
        PreparedContextAssembly contextAssembly
    ) {
        String originalQuery = assembly.requestWithResolvedScope().prompt();
        if (retrievalQueryResolutionService == null) {
            return RetrievalQueryResolution.disabled(originalQuery);
        }
        try {
            return retrievalQueryResolutionService.resolve(
                assembly.requestWithResolvedScope(),
                ChatMode.RAG,
                contextAssembly
            );
        } catch (RuntimeException exception) {
            return RetrievalQueryResolution.errorFallback(originalQuery, exception);
        }
    }

    private RetrievalExecution retrieve(
        PromptAssembly assembly,
        ChatExecutionContext context,
        RetrievalQueryResolution resolution
    ) {
        try {
            context.throwIfCancellationRequested();
            String queryForRetrieval = resolution == null
                ? assembly.requestWithResolvedScope().prompt()
                : resolution.queryForRetrieval();
            MaterialRetrievalResult retrievalResult = retrievalStep.retrieve(
                assembly.requestWithResolvedScope(),
                assembly.scopeContext(),
                queryForRetrieval
            );
            context.throwIfCancellationRequested();
            RetrievalExecution retrievalExecution = applyOriginalFallbackIfNeeded(
                assembly,
                resolution,
                retrievalResult
            );
            retrievalResult = retrievalExecution.result();
            RetrievalQueryResolution finalResolution = retrievalExecution.resolution();
            context.throwIfCancellationRequested();
            resultRecorder.saveRetrievalSummary(
                context,
                "DONE",
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug()
            );
            resultRecorder.transitionStage(context, "RETRIEVAL_DONE");
            return new RetrievalExecution(retrievalResult, finalResolution);
        } catch (ChatRunCancelledException | ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            resultRecorder.saveRetrievalSummary(context, "FAILED", null, null);
            resultRecorder.failTrace(context, "RETRIEVAL", exception);
            throw exception;
        }
    }

    private RetrievalExecution applyOriginalFallbackIfNeeded(
        PromptAssembly assembly,
        RetrievalQueryResolution resolution,
        MaterialRetrievalResult retrievalResult
    ) {
        if (resolution == null || resolution.decision() != RetrievalQueryResolutionDecision.RESOLVED) {
            return new RetrievalExecution(retrievalResult, resolution);
        }
        if (retrievalResult == null || !retrievalResult.matches().isEmpty()) {
            return new RetrievalExecution(retrievalResult, resolution);
        }
        if (retrievalResult.scopedReadyMaterialCount() <= 0) {
            return new RetrievalExecution(retrievalResult, resolution);
        }
        if (resolution.queryForRetrieval() == null
            || resolution.queryForRetrieval().equals(assembly.requestWithResolvedScope().prompt())) {
            return new RetrievalExecution(retrievalResult, resolution);
        }

        MaterialRetrievalResult fallbackResult = retrievalStep.retrieve(
            assembly.requestWithResolvedScope(),
            assembly.scopeContext(),
            assembly.requestWithResolvedScope().prompt()
        );
        if (!fallbackResult.matches().isEmpty()) {
            return new RetrievalExecution(fallbackResult, resolution.fallbackOriginal());
        }
        return new RetrievalExecution(retrievalResult, resolution.degraded("resolved_query_no_hits"));
    }

    private LlmClient.ChatResult executeLlm(
        LlmClient.ChatRequest chatRequest,
        ChatExecutionContext context
    ) {
        Instant llmStartedAt = Instant.now();
        try {
            context.throwIfCancellationRequested();
            LlmClient.ChatResult result = llmExecutionStep.execute(chatRequest, context.cancellationToken());
            context.throwIfCancellationRequested();
            resultRecorder.saveLlmSuccess(context, chatRequest, result);
            resultRecorder.transitionStage(context, "LLM_DONE");
            context.throwIfCancellationRequested();
            return result;
        } catch (ChatRunCancelledException | ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            long latencyMs = Duration.between(llmStartedAt, Instant.now()).toMillis();
            resultRecorder.saveLlmFailure(context, chatRequest, exception, latencyMs);
            resultRecorder.failTrace(context, "LLM", exception);
            throw exception;
        }
    }

    private ChatExecutionResponse completeNoContext(
        ChatMode mode,
        PromptAssembly assembly,
        PreparedContextAssembly contextAssembly,
        ChatExecutionRequestParts requestParts,
        MaterialRetrievalResult retrievalResult,
        String answer,
        List<ChatSource> sources,
        String rawModelAnswer,
        boolean strictSourcesBlockedAnswer,
        ChatExecutionContext context,
        StickyResolution stickyResolution
    ) {
        context.throwIfCancellationRequested();
        return completeWithStickyUpdate(
            context,
            contextAssemblyCoordinator.withContext(
                resultFactory.noContext(
                    mode,
                    assembly.promptPolicy(),
                    requestParts,
                    retrievalResult.retrievalTrace(),
                    retrievalResult.retrievalDebug(),
                    answer,
                    sources,
                    rawModelAnswer,
                    strictSourcesBlockedAnswer
                ),
                contextAssembly
            ),
            assembly,
            stickyResolution,
            contextAssembly
        );
    }

    private ChatExecutionResponse completeWithStickyUpdate(
        ChatExecutionContext context,
        ChatResultFactory.PreparedChatResult preparedResult,
        PromptAssembly assembly,
        StickyResolution stickyResolution,
        PreparedContextAssembly contextAssembly
    ) {
        ChatExecutionResponse response = resultRecorder.complete(context, preparedResult);
        if (stickyStateService != null) {
            stickyStateService.updateAfterCompleted(
                stickyResolution,
                assembly.normalizedRequest(),
                response,
                context.traceContext()
            );
        }
        if (summaryTriggerService != null) {
            summaryTriggerService.afterCompleted(contextAssembly, context.traceContext());
        }
        if (memoryTriggerService != null) {
            memoryTriggerService.afterCompleted(contextAssembly, context.traceContext());
        }
        return response;
    }

    private ChatExecutionRequestParts requestParts(PromptAssembly assembly) {
        KnowledgeScopeResolved resolvedScope = assembly.scopeContext().resolvedScope() == null
            ? KnowledgeScopeResolved.empty()
            : assembly.scopeContext().resolvedScope();
        return new ChatExecutionRequestParts(
            assembly.requestWithResolvedScope().prompt(),
            assembly.instructionTrace(),
            resolvedScope
        );
    }

    private record RetrievalExecution(
        MaterialRetrievalResult result,
        RetrievalQueryResolution resolution
    ) {
    }
}
