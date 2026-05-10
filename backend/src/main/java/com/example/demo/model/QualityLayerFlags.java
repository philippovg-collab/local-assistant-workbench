package com.example.demo.model;

public record QualityLayerFlags(
    boolean metadataV1,
    boolean structuredV1,
    boolean metadataFiltersV1,
    boolean searchApiV1,
    boolean rerankerV1,
    boolean queryHintsV1
) {
    public static QualityLayerFlags none() {
        return new QualityLayerFlags(false, false, false, false, false, false);
    }
}
