package com.example.demo.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ChatRunRequestSnapshot(
    JsonNode requestPayload,
    JsonNode normalizedRequestPayload,
    String prompt,
    JsonNode knowledgeScope,
    JsonNode retrievalFilters
) {
}
