package com.example.demo.infrastructure.material;

public record MaterialIndexingLease(
    StoredMaterialRecord record,
    int attemptNumber
) {
}
