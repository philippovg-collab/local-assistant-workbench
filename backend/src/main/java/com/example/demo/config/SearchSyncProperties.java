package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.search-sync")
public class SearchSyncProperties {

    private boolean enabled = false;
    private String indexPrefix = "rag-chunks";
    private String indexVersion = "v3";
    private int claimBatchSize = 32;
    private int claimLeaseSeconds = 120;
    private int maxAttempts = 3;
    private int retryBaseSeconds = 5;
    private int retryMaxSeconds = 60;
    private int maxDrainBatches = 16;
    private int maxBulkActions = 500;
    private int maxOutstandingSeconds = 120;
    private int maxFailedEventsBeforeFallback = 0;
    private int healthSnapshotTtlSeconds = 15;
    private final OperatorProperties operator = new OperatorProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public int getClaimBatchSize() {
        return claimBatchSize;
    }

    public void setClaimBatchSize(int claimBatchSize) {
        this.claimBatchSize = claimBatchSize;
    }

    public int getClaimLeaseSeconds() {
        return claimLeaseSeconds;
    }

    public void setClaimLeaseSeconds(int claimLeaseSeconds) {
        this.claimLeaseSeconds = claimLeaseSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryBaseSeconds() {
        return retryBaseSeconds;
    }

    public void setRetryBaseSeconds(int retryBaseSeconds) {
        this.retryBaseSeconds = retryBaseSeconds;
    }

    public int getRetryMaxSeconds() {
        return retryMaxSeconds;
    }

    public void setRetryMaxSeconds(int retryMaxSeconds) {
        this.retryMaxSeconds = retryMaxSeconds;
    }

    public int getMaxDrainBatches() {
        return maxDrainBatches;
    }

    public void setMaxDrainBatches(int maxDrainBatches) {
        this.maxDrainBatches = maxDrainBatches;
    }

    public int getMaxBulkActions() {
        return maxBulkActions;
    }

    public void setMaxBulkActions(int maxBulkActions) {
        this.maxBulkActions = maxBulkActions;
    }

    public int getMaxOutstandingSeconds() {
        return maxOutstandingSeconds;
    }

    public void setMaxOutstandingSeconds(int maxOutstandingSeconds) {
        this.maxOutstandingSeconds = maxOutstandingSeconds;
    }

    public int getMaxFailedEventsBeforeFallback() {
        return maxFailedEventsBeforeFallback;
    }

    public void setMaxFailedEventsBeforeFallback(int maxFailedEventsBeforeFallback) {
        this.maxFailedEventsBeforeFallback = maxFailedEventsBeforeFallback;
    }

    public int getHealthSnapshotTtlSeconds() {
        return healthSnapshotTtlSeconds;
    }

    public void setHealthSnapshotTtlSeconds(int healthSnapshotTtlSeconds) {
        this.healthSnapshotTtlSeconds = healthSnapshotTtlSeconds;
    }

    public OperatorProperties getOperator() {
        return operator;
    }

    public String indexName() {
        return indexName(indexVersion);
    }

    public String indexName(String version) {
        return indexPrefix + "-" + version;
    }

    public String readAlias() {
        return indexPrefix + "-read";
    }

    public String writeAlias() {
        return indexPrefix + "-write";
    }

    public static class OperatorProperties {

        private String command;
        private boolean enabled = false;
        private int waitTimeoutSeconds = 300;
        private int pollIntervalMillis = 250;

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWaitTimeoutSeconds() {
            return waitTimeoutSeconds;
        }

        public void setWaitTimeoutSeconds(int waitTimeoutSeconds) {
            this.waitTimeoutSeconds = waitTimeoutSeconds;
        }

        public int getPollIntervalMillis() {
            return pollIntervalMillis;
        }

        public void setPollIntervalMillis(int pollIntervalMillis) {
            this.pollIntervalMillis = pollIntervalMillis;
        }
    }
}
