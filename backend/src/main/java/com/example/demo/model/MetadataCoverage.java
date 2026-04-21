package com.example.demo.model;

public record MetadataCoverage(
    int activeTotal,
    int activeWithEffectiveMetadata,
    double ratio,
    CoverageStat workspace,
    CoverageStat documentType,
    CoverageStat documentStatus
) {
    public static MetadataCoverage empty() {
        return new MetadataCoverage(
            0,
            0,
            0.0d,
            CoverageStat.empty(),
            CoverageStat.empty(),
            CoverageStat.empty()
        );
    }
}
