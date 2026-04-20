package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.audit")
public class ChatAuditProperties {

    private boolean failClosed = false;
    private int healthFailureThreshold = 1;
    private int retentionDays = 30;

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public int getHealthFailureThreshold() {
        return healthFailureThreshold;
    }

    public void setHealthFailureThreshold(int healthFailureThreshold) {
        this.healthFailureThreshold = healthFailureThreshold;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
