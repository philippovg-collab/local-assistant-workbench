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
    private boolean legacyImportEnabled = false;
    private int indexingLeaseSeconds = 120;
    private int indexingMaxAttempts = 3;
    private int indexingRetryBaseSeconds = 5;
    private int indexingRetryMaxSeconds = 60;

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

    public boolean isLegacyImportEnabled() {
        return legacyImportEnabled;
    }

    public void setLegacyImportEnabled(boolean legacyImportEnabled) {
        this.legacyImportEnabled = legacyImportEnabled;
    }

    public int getIndexingLeaseSeconds() {
        return indexingLeaseSeconds;
    }

    public void setIndexingLeaseSeconds(int indexingLeaseSeconds) {
        this.indexingLeaseSeconds = indexingLeaseSeconds;
    }

    public int getIndexingMaxAttempts() {
        return indexingMaxAttempts;
    }

    public void setIndexingMaxAttempts(int indexingMaxAttempts) {
        this.indexingMaxAttempts = indexingMaxAttempts;
    }

    public int getIndexingRetryBaseSeconds() {
        return indexingRetryBaseSeconds;
    }

    public void setIndexingRetryBaseSeconds(int indexingRetryBaseSeconds) {
        this.indexingRetryBaseSeconds = indexingRetryBaseSeconds;
    }

    public int getIndexingRetryMaxSeconds() {
        return indexingRetryMaxSeconds;
    }

    public void setIndexingRetryMaxSeconds(int indexingRetryMaxSeconds) {
        this.indexingRetryMaxSeconds = indexingRetryMaxSeconds;
    }
}
