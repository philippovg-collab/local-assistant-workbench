package com.example.demo.infrastructure.material;

public record QualityLayerCoverageSnapshot(
    int activeTotal,
    int activeWithEffectiveMetadata,
    int documentTypeCovered,
    int sourceTrustCovered,
    int authorOrDepartmentCovered,
    int structuredProfileActive,
    int partialReadyActive
) {
    public static QualityLayerCoverageSnapshot empty() {
        return new QualityLayerCoverageSnapshot(0, 0, 0, 0, 0, 0, 0);
    }
}
