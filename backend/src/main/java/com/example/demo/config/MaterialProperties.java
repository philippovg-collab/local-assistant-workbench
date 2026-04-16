package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.materials")
public class MaterialProperties {

    private int maxUploadBytes = 2_000_000;
    private int maxTextChars = 200_000;
    private int chunkSize = 900;
    private int chunkOverlap = 180;
    private int maxChunks = 24;
    private int extractionTimeoutSeconds = 10;

    public int getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(int maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public int getMaxTextChars() {
        return maxTextChars;
    }

    public void setMaxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    public int getExtractionTimeoutSeconds() {
        return extractionTimeoutSeconds;
    }

    public void setExtractionTimeoutSeconds(int extractionTimeoutSeconds) {
        this.extractionTimeoutSeconds = extractionTimeoutSeconds;
    }
}
