package com.example.demo.service.memory;

import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;

public record MemoryEntryQuery(
    MemoryEntryStatus status,
    MemoryEntryType entryType,
    String workspaceKey,
    String projectKey
) {
}
