package com.example.demo.service.material;

import java.time.Instant;

public record MaterialLineageOperatorOverride(
    String id,
    String lineageKey,
    String sourceKey,
    String reason,
    String createdBy,
    boolean active,
    Instant createdAt,
    Instant deactivatedAt
) {
}
