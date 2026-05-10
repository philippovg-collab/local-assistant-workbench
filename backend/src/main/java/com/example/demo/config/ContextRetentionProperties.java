package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.context.retention")
public class ContextRetentionProperties {

    private boolean enabled = true;
    private int snapshotDays = 30;
    private int summaryJobDays = 14;
    private int memoryRejectedDays = 30;
    private int memoryDeletedDays = 30;
    private int memoryJobDays = 14;
    private int deletedConversationDays = 30;
    private int emptyConversationDays = 7;
    private int keepLatestSnapshotsPerConversation = 5;
    private int batchSize = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSnapshotDays() {
        return Math.max(1, snapshotDays);
    }

    public void setSnapshotDays(int snapshotDays) {
        this.snapshotDays = snapshotDays;
    }

    public int getSummaryJobDays() {
        return Math.max(1, summaryJobDays);
    }

    public void setSummaryJobDays(int summaryJobDays) {
        this.summaryJobDays = summaryJobDays;
    }

    public int getMemoryRejectedDays() {
        return Math.max(1, memoryRejectedDays);
    }

    public void setMemoryRejectedDays(int memoryRejectedDays) {
        this.memoryRejectedDays = memoryRejectedDays;
    }

    public int getMemoryDeletedDays() {
        return Math.max(1, memoryDeletedDays);
    }

    public void setMemoryDeletedDays(int memoryDeletedDays) {
        this.memoryDeletedDays = memoryDeletedDays;
    }

    public int getMemoryJobDays() {
        return Math.max(1, memoryJobDays);
    }

    public void setMemoryJobDays(int memoryJobDays) {
        this.memoryJobDays = memoryJobDays;
    }

    public int getDeletedConversationDays() {
        return Math.max(1, deletedConversationDays);
    }

    public void setDeletedConversationDays(int deletedConversationDays) {
        this.deletedConversationDays = deletedConversationDays;
    }

    public int getEmptyConversationDays() {
        return Math.max(1, emptyConversationDays);
    }

    public void setEmptyConversationDays(int emptyConversationDays) {
        this.emptyConversationDays = emptyConversationDays;
    }

    public int getKeepLatestSnapshotsPerConversation() {
        return Math.max(0, keepLatestSnapshotsPerConversation);
    }

    public void setKeepLatestSnapshotsPerConversation(int keepLatestSnapshotsPerConversation) {
        this.keepLatestSnapshotsPerConversation = keepLatestSnapshotsPerConversation;
    }

    public int getBatchSize() {
        return Math.max(1, batchSize);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
