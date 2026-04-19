package com.example.demo.model;

public record RechunkActiveMaterialsResponse(
    int activeCount,
    int scheduledCount,
    int alreadyCurrentCount,
    int legacyBestEffortCount
) {
}
