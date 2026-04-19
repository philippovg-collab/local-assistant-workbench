package com.example.demo.model;

public record MaterialSearchRequest(
    String query,
    RetrievalFilters filters,
    Integer limit,
    Boolean includeNeighbors,
    Boolean debug
) {

    public MaterialSearchRequest {
        query = query == null ? null : query.trim();
        filters = filters == null ? RetrievalFilters.empty() : filters;
    }

    public boolean includeNeighborsOrDefault() {
        return Boolean.TRUE.equals(includeNeighbors);
    }

    public boolean debugOrDefault() {
        return Boolean.TRUE.equals(debug);
    }
}
