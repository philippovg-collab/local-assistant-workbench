package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.eval.e2e")
public class EvalProperties {

    private boolean reconcilerEnabled = true;
    private long reconcileDelayMs = 5000L;
    private long chatRunTimeoutMs = 0L;

    public boolean isReconcilerEnabled() {
        return reconcilerEnabled;
    }

    public void setReconcilerEnabled(boolean reconcilerEnabled) {
        this.reconcilerEnabled = reconcilerEnabled;
    }

    public long getReconcileDelayMs() {
        return Math.max(1000L, reconcileDelayMs);
    }

    public void setReconcileDelayMs(long reconcileDelayMs) {
        this.reconcileDelayMs = reconcileDelayMs;
    }

    public long getChatRunTimeoutMs() {
        return Math.max(0L, chatRunTimeoutMs);
    }

    public void setChatRunTimeoutMs(long chatRunTimeoutMs) {
        this.chatRunTimeoutMs = chatRunTimeoutMs;
    }
}
