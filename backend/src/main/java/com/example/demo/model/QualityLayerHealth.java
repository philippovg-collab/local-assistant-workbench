package com.example.demo.model;

public record QualityLayerHealth(
    QualityLayerFlags flags,
    MetadataCoverage metadataCoverage,
    ActiveBackfillCoverage activeBackfillCoverage,
    RetrievalWindowHealth retrievalWindow,
    String configuredChunkProfile,
    String effectiveChunkProfile,
    StructuredV1ProofStatus structuredV1ProofStatus
) {
    public QualityLayerHealth {
        flags = flags == null ? QualityLayerFlags.none() : flags;
        metadataCoverage = metadataCoverage == null ? MetadataCoverage.empty() : metadataCoverage;
        activeBackfillCoverage = activeBackfillCoverage == null ? ActiveBackfillCoverage.empty() : activeBackfillCoverage;
        retrievalWindow = retrievalWindow == null ? RetrievalWindowHealth.empty() : retrievalWindow;
        structuredV1ProofStatus = structuredV1ProofStatus == null
            ? StructuredV1ProofStatus.disabled()
            : structuredV1ProofStatus;
    }

    public static QualityLayerHealth empty() {
        return new QualityLayerHealth(
            QualityLayerFlags.none(),
            MetadataCoverage.empty(),
            ActiveBackfillCoverage.empty(),
            RetrievalWindowHealth.empty(),
            null,
            null,
            StructuredV1ProofStatus.disabled()
        );
    }
}
