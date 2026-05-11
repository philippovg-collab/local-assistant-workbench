package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rollout")
public class RolloutProperties {

    private boolean metadataV1 = false;
    private boolean structuredV1 = false;
    private boolean metadataFiltersV1 = false;
    private boolean searchApiV1 = false;
    private boolean rerankerV1 = false;
    private boolean queryHintsV1 = false;
    private String structuredV1ProofCompareId;

    public static RolloutProperties enabledForTests() {
        RolloutProperties properties = new RolloutProperties();
        properties.setMetadataV1(true);
        properties.setStructuredV1(true);
        properties.setMetadataFiltersV1(true);
        properties.setSearchApiV1(true);
        properties.setRerankerV1(true);
        properties.setQueryHintsV1(true);
        properties.setStructuredV1ProofCompareId("test-override");
        return properties;
    }

    public boolean isMetadataV1() {
        return metadataV1;
    }

    public void setMetadataV1(boolean metadataV1) {
        this.metadataV1 = metadataV1;
    }

    public boolean isStructuredV1() {
        return structuredV1;
    }

    public void setStructuredV1(boolean structuredV1) {
        this.structuredV1 = structuredV1;
    }

    public boolean isMetadataFiltersV1() {
        return metadataFiltersV1;
    }

    public void setMetadataFiltersV1(boolean metadataFiltersV1) {
        this.metadataFiltersV1 = metadataFiltersV1;
    }

    public boolean isSearchApiV1() {
        return searchApiV1;
    }

    public void setSearchApiV1(boolean searchApiV1) {
        this.searchApiV1 = searchApiV1;
    }

    public boolean isRerankerV1() {
        return rerankerV1;
    }

    public void setRerankerV1(boolean rerankerV1) {
        this.rerankerV1 = rerankerV1;
    }

    public boolean isQueryHintsV1() {
        return queryHintsV1;
    }

    public void setQueryHintsV1(boolean queryHintsV1) {
        this.queryHintsV1 = queryHintsV1;
    }

    public String getStructuredV1ProofCompareId() {
        return structuredV1ProofCompareId;
    }

    public void setStructuredV1ProofCompareId(String structuredV1ProofCompareId) {
        this.structuredV1ProofCompareId = structuredV1ProofCompareId;
    }
}
