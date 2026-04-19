package com.example.demo.model;

import com.example.demo.config.RolloutProperties;

public record QualityLayerFlags(
    boolean metadataV1,
    boolean structuredV1,
    boolean metadataFiltersV1,
    boolean searchApiV1,
    boolean rerankerV1,
    boolean queryHintsV1
) {
    public static QualityLayerFlags from(RolloutProperties properties) {
        if (properties == null) {
            return none();
        }
        return new QualityLayerFlags(
            properties.isMetadataV1(),
            properties.isStructuredV1(),
            properties.isMetadataFiltersV1(),
            properties.isSearchApiV1(),
            properties.isRerankerV1(),
            properties.isQueryHintsV1()
        );
    }

    public static QualityLayerFlags none() {
        return new QualityLayerFlags(false, false, false, false, false, false);
    }
}
