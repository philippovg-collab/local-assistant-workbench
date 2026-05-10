package com.example.demo.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatExecutionRequest(
    ChatMode mode,
    @Size(max = 128)
    String model,
    @Size(max = 20000)
    String prompt,
    @Size(max = 8000)
    String systemPrompt,
    @Size(max = 32)
    List<String> instructionIds,
    AnswerMode answerMode,
    @Valid
    KnowledgeScope knowledgeScope,
    @Size(max = 128)
    String instructionWorkspaceKey,
    @Valid
    RetrievalFilters retrievalFilters,
    @Size(max = 32)
    List<String> dismissedRetrievalHintKeys,
    @Size(max = 32)
    List<String> scenarioInstructionIds,
    @Size(max = 8000)
    String temporaryInstruction,
    @Size(max = 36)
    String conversationId,
    @Size(max = 36)
    String parentRunId,
    @Size(max = 128)
    String clientTurnId,
    Boolean persistConversation,
    Boolean contextDebug,
    @Valid
    ContextOptions contextOptions
) {
    public ChatExecutionRequest(
        ChatMode mode,
        String model,
        String prompt,
        String systemPrompt,
        List<String> instructionIds
    ) {
        this(mode, model, prompt, systemPrompt, instructionIds, null, null, null, null, null, null, null);
    }

    public ChatExecutionRequest(
        ChatMode mode,
        String model,
        String prompt,
        String systemPrompt,
        List<String> instructionIds,
        AnswerMode answerMode,
        KnowledgeScope knowledgeScope,
        List<String> scenarioInstructionIds,
        String temporaryInstruction
    ) {
        this(
            mode,
            model,
            prompt,
            systemPrompt,
            instructionIds,
            answerMode,
            knowledgeScope,
            null,
            null,
            null,
            scenarioInstructionIds,
            temporaryInstruction
        );
    }

    public ChatExecutionRequest(
        ChatMode mode,
        String model,
        String prompt,
        String systemPrompt,
        List<String> instructionIds,
        AnswerMode answerMode,
        KnowledgeScope knowledgeScope,
        String instructionWorkspaceKey,
        RetrievalFilters retrievalFilters,
        List<String> scenarioInstructionIds,
        String temporaryInstruction
    ) {
        this(
            mode,
            model,
            prompt,
            systemPrompt,
            instructionIds,
            answerMode,
            knowledgeScope,
            instructionWorkspaceKey,
            retrievalFilters,
            null,
            scenarioInstructionIds,
            temporaryInstruction
        );
    }

    public ChatExecutionRequest(
        ChatMode mode,
        String model,
        String prompt,
        String systemPrompt,
        List<String> instructionIds,
        AnswerMode answerMode,
        KnowledgeScope knowledgeScope,
        String instructionWorkspaceKey,
        RetrievalFilters retrievalFilters,
        List<String> dismissedRetrievalHintKeys,
        List<String> scenarioInstructionIds,
        String temporaryInstruction
    ) {
        this(
            mode,
            model,
            prompt,
            systemPrompt,
            instructionIds,
            answerMode,
            knowledgeScope,
            instructionWorkspaceKey,
            retrievalFilters,
            dismissedRetrievalHintKeys,
            scenarioInstructionIds,
            temporaryInstruction,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
