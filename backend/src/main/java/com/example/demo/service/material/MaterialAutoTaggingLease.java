package com.example.demo.service.material;

public record MaterialAutoTaggingLease(
    MaterialAutoTaggingTask task,
    int attemptNumber
) {
}
