package com.example.demo.model;

import java.util.List;

public record RechunkActiveMaterialsBatchResponse(
    int totalActive,
    int scanned,
    int scheduled,
    int alreadyCurrent,
    int legacyBestEffort,
    String nextCursor,
    List<String> materialIds
) {
}
