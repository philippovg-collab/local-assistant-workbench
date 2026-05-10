package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.context")
public class ContextProperties {

    private boolean enabled = false;
    private boolean conversationsEnabled = false;
    private boolean historyEnabled = false;
    private boolean stickyStateEnabled = false;
    private boolean retrievalQueryResolutionEnabled = false;
    private boolean summaryEnabled = false;
    private boolean longTermMemoryEnabled = false;
    private int maxRecentTurns = 6;
    private int maxHistoryTokens = 2000;
    private int summaryRefreshTurns = 6;
    private int summaryMaxInputTurns = 12;
    private int summaryMaxOutputChars = 2500;
    private int summaryMaxFacts = 12;
    private int summaryMaxActiveEntities = 20;
    private int summaryMaxSourceRefs = 20;
    private int summaryMaxAttempts = 3;
    private int summaryRetryBaseSeconds = 30;
    private int summaryRetryMaxSeconds = 300;
    private int memoryExtractionLeaseSeconds = 120;
    private int memoryExtractionMaxAttempts = 3;
    private int memoryExtractionRetryBaseSeconds = 30;
    private int memoryExtractionRetryMaxSeconds = 300;
    private int memoryExtractionWorkerCount = 1;
    private int memoryExtractionDrainMaxJobs = 50;
    private int memorySelectionLimit = 8;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConversationsEnabled() {
        return enabled && conversationsEnabled;
    }

    public boolean isRawConversationsEnabled() {
        return conversationsEnabled;
    }

    public void setConversationsEnabled(boolean conversationsEnabled) {
        this.conversationsEnabled = conversationsEnabled;
    }

    public boolean isHistoryEnabled() {
        return enabled && historyEnabled;
    }

    public boolean isRawHistoryEnabled() {
        return historyEnabled;
    }

    public void setHistoryEnabled(boolean historyEnabled) {
        this.historyEnabled = historyEnabled;
    }

    public boolean isStickyStateEnabled() {
        return enabled && conversationsEnabled && stickyStateEnabled;
    }

    public boolean isRawStickyStateEnabled() {
        return stickyStateEnabled;
    }

    public void setStickyStateEnabled(boolean stickyStateEnabled) {
        this.stickyStateEnabled = stickyStateEnabled;
    }

    public boolean isRetrievalQueryResolutionEnabled() {
        return enabled && conversationsEnabled && historyEnabled && retrievalQueryResolutionEnabled;
    }

    public boolean isRawRetrievalQueryResolutionEnabled() {
        return retrievalQueryResolutionEnabled;
    }

    public void setRetrievalQueryResolutionEnabled(boolean retrievalQueryResolutionEnabled) {
        this.retrievalQueryResolutionEnabled = retrievalQueryResolutionEnabled;
    }

    public boolean isSummaryEnabled() {
        return enabled && conversationsEnabled && historyEnabled && summaryEnabled;
    }

    public boolean isRawSummaryEnabled() {
        return summaryEnabled;
    }

    public void setSummaryEnabled(boolean summaryEnabled) {
        this.summaryEnabled = summaryEnabled;
    }

    public boolean isLongTermMemoryEnabled() {
        return enabled && conversationsEnabled && historyEnabled && longTermMemoryEnabled;
    }

    public boolean isRawLongTermMemoryEnabled() {
        return longTermMemoryEnabled;
    }

    public void setLongTermMemoryEnabled(boolean longTermMemoryEnabled) {
        this.longTermMemoryEnabled = longTermMemoryEnabled;
    }

    public int getMaxRecentTurns() {
        return maxRecentTurns;
    }

    public void setMaxRecentTurns(int maxRecentTurns) {
        this.maxRecentTurns = maxRecentTurns;
    }

    public int getMaxHistoryTokens() {
        return maxHistoryTokens;
    }

    public void setMaxHistoryTokens(int maxHistoryTokens) {
        this.maxHistoryTokens = maxHistoryTokens;
    }

    public int getSummaryRefreshTurns() {
        return Math.max(1, summaryRefreshTurns);
    }

    public void setSummaryRefreshTurns(int summaryRefreshTurns) {
        this.summaryRefreshTurns = summaryRefreshTurns;
    }

    public int getSummaryMaxInputTurns() {
        return Math.max(1, summaryMaxInputTurns);
    }

    public void setSummaryMaxInputTurns(int summaryMaxInputTurns) {
        this.summaryMaxInputTurns = summaryMaxInputTurns;
    }

    public int getSummaryMaxOutputChars() {
        return Math.max(1, summaryMaxOutputChars);
    }

    public void setSummaryMaxOutputChars(int summaryMaxOutputChars) {
        this.summaryMaxOutputChars = summaryMaxOutputChars;
    }

    public int getSummaryMaxFacts() {
        return Math.max(0, summaryMaxFacts);
    }

    public void setSummaryMaxFacts(int summaryMaxFacts) {
        this.summaryMaxFacts = summaryMaxFacts;
    }

    public int getSummaryMaxActiveEntities() {
        return Math.max(0, summaryMaxActiveEntities);
    }

    public void setSummaryMaxActiveEntities(int summaryMaxActiveEntities) {
        this.summaryMaxActiveEntities = summaryMaxActiveEntities;
    }

    public int getSummaryMaxSourceRefs() {
        return Math.max(0, summaryMaxSourceRefs);
    }

    public void setSummaryMaxSourceRefs(int summaryMaxSourceRefs) {
        this.summaryMaxSourceRefs = summaryMaxSourceRefs;
    }

    public int getSummaryMaxAttempts() {
        return Math.max(1, summaryMaxAttempts);
    }

    public void setSummaryMaxAttempts(int summaryMaxAttempts) {
        this.summaryMaxAttempts = summaryMaxAttempts;
    }

    public int getSummaryRetryBaseSeconds() {
        return Math.max(1, summaryRetryBaseSeconds);
    }

    public void setSummaryRetryBaseSeconds(int summaryRetryBaseSeconds) {
        this.summaryRetryBaseSeconds = summaryRetryBaseSeconds;
    }

    public int getSummaryRetryMaxSeconds() {
        return Math.max(getSummaryRetryBaseSeconds(), summaryRetryMaxSeconds);
    }

    public void setSummaryRetryMaxSeconds(int summaryRetryMaxSeconds) {
        this.summaryRetryMaxSeconds = summaryRetryMaxSeconds;
    }

    public int getMemoryExtractionLeaseSeconds() {
        return Math.max(1, memoryExtractionLeaseSeconds);
    }

    public void setMemoryExtractionLeaseSeconds(int memoryExtractionLeaseSeconds) {
        this.memoryExtractionLeaseSeconds = memoryExtractionLeaseSeconds;
    }

    public int getMemoryExtractionMaxAttempts() {
        return Math.max(1, memoryExtractionMaxAttempts);
    }

    public void setMemoryExtractionMaxAttempts(int memoryExtractionMaxAttempts) {
        this.memoryExtractionMaxAttempts = memoryExtractionMaxAttempts;
    }

    public int getMemoryExtractionRetryBaseSeconds() {
        return Math.max(1, memoryExtractionRetryBaseSeconds);
    }

    public void setMemoryExtractionRetryBaseSeconds(int memoryExtractionRetryBaseSeconds) {
        this.memoryExtractionRetryBaseSeconds = memoryExtractionRetryBaseSeconds;
    }

    public int getMemoryExtractionRetryMaxSeconds() {
        return Math.max(getMemoryExtractionRetryBaseSeconds(), memoryExtractionRetryMaxSeconds);
    }

    public void setMemoryExtractionRetryMaxSeconds(int memoryExtractionRetryMaxSeconds) {
        this.memoryExtractionRetryMaxSeconds = memoryExtractionRetryMaxSeconds;
    }

    public int getMemoryExtractionWorkerCount() {
        return Math.max(1, memoryExtractionWorkerCount);
    }

    public void setMemoryExtractionWorkerCount(int memoryExtractionWorkerCount) {
        this.memoryExtractionWorkerCount = memoryExtractionWorkerCount;
    }

    public int getMemoryExtractionDrainMaxJobs() {
        return Math.max(1, memoryExtractionDrainMaxJobs);
    }

    public void setMemoryExtractionDrainMaxJobs(int memoryExtractionDrainMaxJobs) {
        this.memoryExtractionDrainMaxJobs = memoryExtractionDrainMaxJobs;
    }

    public int getMemorySelectionLimit() {
        return Math.max(0, memorySelectionLimit);
    }

    public void setMemorySelectionLimit(int memorySelectionLimit) {
        this.memorySelectionLimit = memorySelectionLimit;
    }
}
