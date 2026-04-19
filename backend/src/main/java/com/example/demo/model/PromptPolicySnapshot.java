package com.example.demo.model;

import java.util.List;

public record PromptPolicySnapshot(
    String baseSystemPrompt,
    String systemInstructionsText,
    String safetyInstructionsText,
    String contextInstructionsText,
    String userInstructionsText,
    String temporaryInstructionText,
    String answerModeBlockText,
    String groundingBlockText,
    String resolvedSystemPrompt,
    List<ChatRunMessage> messages,
    String promptHash,
    List<InstructionTraceEntry> instructionTrace,
    KnowledgeScopeResolved knowledgeScopeResolved,
    boolean groundingRulesApplied
) {
    public PromptPolicySnapshot {
        messages = messages == null ? List.of() : List.copyOf(messages);
        instructionTrace = instructionTrace == null ? List.of() : List.copyOf(instructionTrace);
        knowledgeScopeResolved = knowledgeScopeResolved == null ? KnowledgeScopeResolved.empty() : knowledgeScopeResolved;
    }

    public PromptPolicySnapshot withMessages(List<ChatRunMessage> nextMessages) {
        return new PromptPolicySnapshot(
            baseSystemPrompt,
            systemInstructionsText,
            safetyInstructionsText,
            contextInstructionsText,
            userInstructionsText,
            temporaryInstructionText,
            answerModeBlockText,
            groundingBlockText,
            resolvedSystemPrompt,
            nextMessages,
            promptHash,
            instructionTrace,
            knowledgeScopeResolved,
            groundingRulesApplied
        );
    }

    public PromptPolicySnapshot withTrace(
        List<InstructionTraceEntry> nextInstructionTrace,
        KnowledgeScopeResolved nextKnowledgeScopeResolved
    ) {
        return new PromptPolicySnapshot(
            baseSystemPrompt,
            systemInstructionsText,
            safetyInstructionsText,
            contextInstructionsText,
            userInstructionsText,
            temporaryInstructionText,
            answerModeBlockText,
            groundingBlockText,
            resolvedSystemPrompt,
            messages,
            promptHash,
            nextInstructionTrace,
            nextKnowledgeScopeResolved,
            groundingRulesApplied
        );
    }
}
