package com.example.demo.model;

import java.util.List;

public record ChatExecutionResponse(
    ChatMode mode,
    String model,
    String prompt,
    String answer,
    String contextStatus,
    String createdAt,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    AnswerMode answerModeApplied,
    List<AppliedInstruction> appliedInstructions,
    List<InstructionTraceEntry> instructionTrace,
    KnowledgeScopeResolved knowledgeScopeResolved,
    RetrievalTrace retrievalTrace,
    RetrievalDebug retrievalDebug,
    List<ChatSource> sources,
    String auditRunId,
    String conversationId,
    Integer turnNo,
    String contextAssemblyId,
    ContextSummary contextSummary
) {
    public ChatExecutionResponse {
        appliedInstructions = appliedInstructions == null ? List.of() : List.copyOf(appliedInstructions);
        instructionTrace = instructionTrace == null ? List.of() : List.copyOf(instructionTrace);
        knowledgeScopeResolved = knowledgeScopeResolved == null ? KnowledgeScopeResolved.empty() : knowledgeScopeResolved;
        retrievalTrace = retrievalTrace == null ? new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0) : retrievalTrace;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public ChatExecutionResponse(
        ChatMode mode,
        String model,
        String prompt,
        String answer,
        String contextStatus,
        String createdAt,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        AnswerMode answerModeApplied,
        List<AppliedInstruction> appliedInstructions,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved,
        RetrievalTrace retrievalTrace,
        List<ChatSource> sources,
        String auditRunId
    ) {
        this(
            mode,
            model,
            prompt,
            answer,
            contextStatus,
            createdAt,
            promptTokens,
            completionTokens,
            totalTokens,
            answerModeApplied,
            appliedInstructions,
            instructionTrace,
            knowledgeScopeResolved,
            retrievalTrace,
            null,
            sources,
            auditRunId,
            null,
            null,
            null,
            null
        );
    }

    public ChatExecutionResponse(
        ChatMode mode,
        String model,
        String prompt,
        String answer,
        String contextStatus,
        String createdAt,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        AnswerMode answerModeApplied,
        List<AppliedInstruction> appliedInstructions,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved,
        RetrievalTrace retrievalTrace,
        RetrievalDebug retrievalDebug,
        List<ChatSource> sources,
        String auditRunId
    ) {
        this(
            mode,
            model,
            prompt,
            answer,
            contextStatus,
            createdAt,
            promptTokens,
            completionTokens,
            totalTokens,
            answerModeApplied,
            appliedInstructions,
            instructionTrace,
            knowledgeScopeResolved,
            retrievalTrace,
            retrievalDebug,
            sources,
            auditRunId,
            null,
            null,
            null,
            null
        );
    }

    public ChatExecutionResponse(
        ChatMode mode,
        String model,
        String prompt,
        String answer,
        String contextStatus,
        String createdAt,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        List<AppliedInstruction> appliedInstructions,
        List<ChatSource> sources
    ) {
        this(
            mode,
            model,
            prompt,
            answer,
            contextStatus,
            createdAt,
            promptTokens,
            completionTokens,
            totalTokens,
            null,
            appliedInstructions == null ? List.of() : appliedInstructions,
            List.of(),
            KnowledgeScopeResolved.empty(),
            null,
            null,
            sources == null ? List.of() : sources,
            null,
            null,
            null,
            null,
            null
        );
    }

    public ChatExecutionResponse withConversationMetadata(
        String conversationId,
        Integer turnNo,
        String contextAssemblyId,
        ContextSummary contextSummary
    ) {
        return new ChatExecutionResponse(
            mode,
            model,
            prompt,
            answer,
            contextStatus,
            createdAt,
            promptTokens,
            completionTokens,
            totalTokens,
            answerModeApplied,
            appliedInstructions,
            instructionTrace,
            knowledgeScopeResolved,
            retrievalTrace,
            retrievalDebug,
            sources,
            auditRunId,
            conversationId,
            turnNo,
            contextAssemblyId,
            contextSummary
        );
    }
}
