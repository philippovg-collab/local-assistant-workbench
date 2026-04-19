package com.example.demo.model;

public record QualityLayerHealth(
    QualityLayerFlags flags,
    MetadataCoverage metadataCoverage,
    ActiveBackfillCoverage activeBackfillCoverage,
    RetrievalWindowHealth retrievalWindow
) {
    public QualityLayerHealth {
        flags = flags == null ? QualityLayerFlags.none() : flags;
        metadataCoverage = metadataCoverage == null ? MetadataCoverage.empty() : metadataCoverage;
        activeBackfillCoverage = activeBackfillCoverage == null ? ActiveBackfillCoverage.empty() : activeBackfillCoverage;
        retrievalWindow = retrievalWindow == null ? RetrievalWindowHealth.empty() : retrievalWindow;
    }

    public static QualityLayerHealth empty() {
        return new QualityLayerHealth(
            QualityLayerFlags.none(),
            MetadataCoverage.empty(),
            ActiveBackfillCoverage.empty(),
            RetrievalWindowHealth.empty()
        );
    }
}
