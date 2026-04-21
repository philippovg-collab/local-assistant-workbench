package com.example.demo.service.material;

public record QualityLayerCoverageSnapshot(
    int activeTotal,
    int activeWithEffectiveMetadata,
    int workspaceCovered,
    int documentTypeCovered,
    int documentStatusCovered,
    int structuredProfileActive,
    int partialReadyActive
) {
    public static QualityLayerCoverageSnapshot empty() {
        return new QualityLayerCoverageSnapshot(0, 0, 0, 0, 0, 0, 0);
    }
}
