package com.example.demo.model;

import java.time.Instant;

public record ChatRunSubmissionResponse(
    String id,
    String status,
    Instant createdAt,
    String statusUrl,
    String traceUrl,
    String resultUrl
) {
    public ChatRunSubmissionResponse(
        String id,
        String status,
        Instant createdAt,
        String traceUrl,
        String resultUrl
    ) {
        this(id, status, createdAt, "/api/chat-runs/" + id + "/status", traceUrl, resultUrl);
    }
}
