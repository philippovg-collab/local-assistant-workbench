package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.chat-execution")
public class ChatExecutionProperties {

    private int threads = 2;
    private int queueCapacity = 32;
    private int pollIntervalMillis = 1000;
    private int claimLeaseSeconds = 300;
    private int maxAttempts = 2;
    private long heartbeatIntervalMillis = 0;
    private int compatibilityWaitTimeoutSeconds = 30;

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(int pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
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

    public long getHeartbeatIntervalMillis() {
        if (heartbeatIntervalMillis > 0) {
            return Math.max(1000L, heartbeatIntervalMillis);
        }
        long leaseMillis = Math.max(1, claimLeaseSeconds) * 1000L;
        return Math.max(1000L, Math.min(leaseMillis / 3L, 30000L));
    }

    public void setHeartbeatIntervalMillis(long heartbeatIntervalMillis) {
        this.heartbeatIntervalMillis = heartbeatIntervalMillis;
    }

    public int getCompatibilityWaitTimeoutSeconds() {
        return Math.min(120, Math.max(1, compatibilityWaitTimeoutSeconds));
    }

    public void setCompatibilityWaitTimeoutSeconds(int compatibilityWaitTimeoutSeconds) {
        this.compatibilityWaitTimeoutSeconds = compatibilityWaitTimeoutSeconds;
    }
}
