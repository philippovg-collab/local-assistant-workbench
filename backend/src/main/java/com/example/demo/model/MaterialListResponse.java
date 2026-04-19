package com.example.demo.model;

import java.util.List;

public record MaterialListResponse(
    List<MaterialSummary> items,
    int total,
    int offset,
    int limit,
    boolean hasMore
) {
}
