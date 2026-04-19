package com.example.demo.model;

public record RechunkActiveMaterialsBatchRequest(
    Integer limit,
    String cursor,
    Boolean dryRun
) {
}
