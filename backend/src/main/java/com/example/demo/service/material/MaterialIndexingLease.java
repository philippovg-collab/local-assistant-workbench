package com.example.demo.service.material;

public record MaterialIndexingLease(
    StoredMaterialRecord record,
    int attemptNumber
) {
}
