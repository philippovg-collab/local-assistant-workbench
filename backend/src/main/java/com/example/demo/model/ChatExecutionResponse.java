package com.example.demo.model;

import java.util.List;

public record ChatExecutionResponse(
    ChatMode mode,
    String model,
    String prompt,
    String answer,
    String createdAt,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    List<AppliedInstruction> appliedInstructions,
    List<ChatSource> sources
) {
}
