package com.example.demo.model;

public record StructuredV1ProofStatus(
    String status,
    String compareId,
    String reasonCode,
    String reasonMessage
) {
    public static StructuredV1ProofStatus disabled() {
        return new StructuredV1ProofStatus(
            "DISABLED",
            null,
            "structured_v1.disabled",
            "Structured-v1 rollout is disabled."
        );
    }

    public static StructuredV1ProofStatus compatible(String compareId) {
        return new StructuredV1ProofStatus(
            "COMPATIBLE",
            compareId,
            null,
            null
        );
    }
}
