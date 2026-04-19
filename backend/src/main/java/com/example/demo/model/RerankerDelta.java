package com.example.demo.model;

public record RerankerDelta(
    int top1ChangedCount,
    int top1ImprovedCount,
    int appendixDemotions,
    int highTrustPromotions
) {
    public static RerankerDelta empty() {
        return new RerankerDelta(0, 0, 0, 0);
    }
}
