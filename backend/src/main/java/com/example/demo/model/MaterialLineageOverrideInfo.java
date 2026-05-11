package com.example.demo.model;

import java.time.Instant;

public record MaterialLineageOverrideInfo(
    String lineageKey,
    String sourceKey,
    boolean active,
    String reason,
    String createdBy,
    Instant createdAt
) {
}
