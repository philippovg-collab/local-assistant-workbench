package com.example.demo.model;

public record MetadataCoverage(
    int activeTotal,
    int activeWithEffectiveMetadata,
    double ratio,
    CoverageStat documentType,
    CoverageStat sourceTrust,
    CoverageStat authorOrDepartment
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
