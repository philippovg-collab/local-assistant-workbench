package com.example.demo.model;

import java.time.Instant;
import java.util.List;

public record LlmCallTrace(
    String id,
    String provider,
    String model,
    List<ChatRunMessage> requestMessages,
    String rawResponseText,
    String parsedAnswerText,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long latencyMs,
    int retryCount,
    Integer timeoutSeconds,
    String finishReason,
    String errorCode,
    String errorMessage,
    Instant createdAt
) {
    public LlmCallTrace {
        requestMessages = requestMessages == null ? List.of() : List.copyOf(requestMessages);
    }
}
