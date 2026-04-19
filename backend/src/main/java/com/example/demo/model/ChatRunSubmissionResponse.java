package com.example.demo.model;

import java.time.Instant;

public record ChatRunSubmissionResponse(
    String id,
    String status,
    Instant createdAt,
    String traceUrl,
    String resultUrl
) {
}
