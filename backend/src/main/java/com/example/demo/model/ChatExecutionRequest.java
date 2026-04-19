package com.example.demo.model;

import java.util.List;

public record ChatExecutionRequest(
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
}
