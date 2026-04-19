package com.example.demo.config;

import com.example.demo.infrastructure.material.LexicalProviderMode;
import com.example.demo.service.RelevanceProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int semanticCandidateLimit = 12;
    private int lexicalCandidateLimit = 12;
    private int finalContextLimit = 4;
    private int maxSearchLimit = 20;
    private int rerankCandidateLimit = 12;
    private double maxSemanticDistance = 0.72d;
    private String lexicalProvider = LexicalProviderMode.POSTGRES.propertyValue();
    private String relevanceProfile = RelevanceProfile.HYBRID_RERANK_V1.propertyValue();
    private boolean shadowEnabled = false;
    private int shadowSamplePercent = 10;

    public int getSemanticCandidateLimit() {
        return semanticCandidateLimit;
    }

    public void setSemanticCandidateLimit(int semanticCandidateLimit) {
        this.semanticCandidateLimit = semanticCandidateLimit;
    }

    public int getLexicalCandidateLimit() {
        return lexicalCandidateLimit;
    }

    public void setLexicalCandidateLimit(int lexicalCandidateLimit) {
        this.lexicalCandidateLimit = lexicalCandidateLimit;
    }

    public int getFinalContextLimit() {
        return finalContextLimit;
    }

    public void setFinalContextLimit(int finalContextLimit) {
        this.finalContextLimit = finalContextLimit;
    }

    public int getMaxSearchLimit() {
        return maxSearchLimit;
    }

    public void setMaxSearchLimit(int maxSearchLimit) {
        this.maxSearchLimit = maxSearchLimit;
    }

    public int getRerankCandidateLimit() {
        return rerankCandidateLimit;
    }

    public void setRerankCandidateLimit(int rerankCandidateLimit) {
        this.rerankCandidateLimit = rerankCandidateLimit;
    }

    public double getMaxSemanticDistance() {
        return maxSemanticDistance;
    }

    public void setMaxSemanticDistance(double maxSemanticDistance) {
        this.maxSemanticDistance = maxSemanticDistance;
    }

    public String getLexicalProvider() {
        return lexicalProvider;
    }

    public void setLexicalProvider(String lexicalProvider) {
        this.lexicalProvider = lexicalProvider;
    }

    public String getRelevanceProfile() {
        return relevanceProfile;
    }

    public void setRelevanceProfile(String relevanceProfile) {
        this.relevanceProfile = relevanceProfile;
    }

    public boolean isShadowEnabled() {
        return shadowEnabled;
    }

    public void setShadowEnabled(boolean shadowEnabled) {
        this.shadowEnabled = shadowEnabled;
    }

    public int getShadowSamplePercent() {
        return shadowSamplePercent;
    }

    public void setShadowSamplePercent(int shadowSamplePercent) {
        this.shadowSamplePercent = shadowSamplePercent;
    }
}
