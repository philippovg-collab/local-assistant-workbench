package com.example.demo.service.memory;

public record MemoryExtractionLease(
    MemoryExtractionJob job,
    int attemptNumber
) {
}
