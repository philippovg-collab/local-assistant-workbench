package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int semanticCandidateLimit = 12;
    private int lexicalCandidateLimit = 12;
    private int finalContextLimit = 4;
    private double maxSemanticDistance = 0.72d;

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

    public double getMaxSemanticDistance() {
        return maxSemanticDistance;
    }

    public void setMaxSemanticDistance(double maxSemanticDistance) {
        this.maxSemanticDistance = maxSemanticDistance;
    }
}
