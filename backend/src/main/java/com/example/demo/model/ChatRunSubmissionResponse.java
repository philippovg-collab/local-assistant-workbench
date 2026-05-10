package com.example.demo.model;

import java.time.Instant;

public record ChatRunSubmissionResponse(
    String id,
    String status,
    Instant createdAt,
    String statusUrl,
    String traceUrl,
    String resultUrl,
    String conversationId,
    Integer turnNo,
    String cancelUrl
) {
    public ChatRunSubmissionResponse(
        String id,
        String status,
        Instant createdAt,
        String statusUrl,
        String traceUrl,
        String resultUrl
    ) {
        this(id, status, createdAt, statusUrl, traceUrl, resultUrl, null, null, "/api/chat-runs/" + id + "/cancel");
    }

    public ChatRunSubmissionResponse(
        String id,
        String status,
        Instant createdAt,
        String traceUrl,
        String resultUrl
    ) {
        this(id, status, createdAt, "/api/chat-runs/" + id + "/status", traceUrl, resultUrl);
    }

    public ChatRunSubmissionResponse withConversation(String conversationId, Integer turnNo) {
        return new ChatRunSubmissionResponse(
            id,
            status,
            createdAt,
            statusUrl,
            traceUrl,
            resultUrl,
            conversationId,
            turnNo,
            cancelUrl
        );
    }
}
