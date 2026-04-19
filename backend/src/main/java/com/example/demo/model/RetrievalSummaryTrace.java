package com.example.demo.model;

import com.fasterxml.jackson.databind.JsonNode;

public record RetrievalSummaryTrace(
    String retrievalStatus,
    RetrievalTrace trace,
    RetrievalDebug debug,
    String lexicalProvider,
    String relevanceProfile,
    String embeddingModel,
    String chunkProfile,
    JsonNode queryHints,
    JsonNode manualFilters,
    JsonNode effectiveFilters,
    JsonNode rolloutFlags,
    JsonNode appliedCapabilities
) {
}
