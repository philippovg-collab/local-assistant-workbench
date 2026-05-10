package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmProviderProbeResult(
    String id,
    LlmProviderStatus status,
    String checkedAt,
    Long latencyMs,
    boolean modelsAvailable,
    boolean chatAvailable,
    boolean embeddingAvailable,
    String errorCode,
    String errorMessage
) {
}
