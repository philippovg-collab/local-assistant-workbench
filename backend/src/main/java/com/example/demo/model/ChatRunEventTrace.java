package com.example.demo.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ChatRunEventTrace(
    String id,
    String eventType,
    JsonNode eventPayload,
    Instant createdAt
) {
}
