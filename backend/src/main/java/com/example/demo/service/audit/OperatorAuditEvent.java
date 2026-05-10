package com.example.demo.service.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OperatorAuditEvent(
    UUID id,
    Instant occurredAt,
    String actor,
    String eventType,
    String outcome,
    String requestId,
    String method,
    String path,
    String entityType,
    String entityId,
    String workspaceKey,
    Integer statusCode,
    String failureCode,
    String failureMessage,
    String remoteAddr,
    String userAgent,
    Map<String, Object> metadata
) {
    public OperatorAuditEvent {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
