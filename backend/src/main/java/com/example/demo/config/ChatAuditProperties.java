package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.audit")
public class ChatAuditProperties {

    private boolean failClosed = false;
    private int healthFailureThreshold = 1;
    private int retentionDays = 30;
    private boolean redactionEnabled = true;
    private boolean storeRawLlmResponse = false;
    private boolean storeRequestMessages = true;
    private int maxStoredTextChars = 12000;

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

    public boolean isRedactionEnabled() {
        return redactionEnabled;
    }

    public void setRedactionEnabled(boolean redactionEnabled) {
        this.redactionEnabled = redactionEnabled;
    }

    public boolean isStoreRawLlmResponse() {
        return storeRawLlmResponse;
    }

    public void setStoreRawLlmResponse(boolean storeRawLlmResponse) {
        this.storeRawLlmResponse = storeRawLlmResponse;
    }

    public boolean isStoreRequestMessages() {
        return storeRequestMessages;
    }

    public void setStoreRequestMessages(boolean storeRequestMessages) {
        this.storeRequestMessages = storeRequestMessages;
    }

    public int getMaxStoredTextChars() {
        return maxStoredTextChars;
    }

    public void setMaxStoredTextChars(int maxStoredTextChars) {
        this.maxStoredTextChars = maxStoredTextChars;
    }
}
