package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.health")
public class HealthProperties {

    private int readinessCacheSeconds = 30;
    private long readinessProbeIntervalMillis = 30_000;
    private long readinessInitialDelayMillis = 1_000;

    public int getReadinessCacheSeconds() {
        return readinessCacheSeconds;
    }

    public void setReadinessCacheSeconds(int readinessCacheSeconds) {
        this.readinessCacheSeconds = readinessCacheSeconds;
    }

    public long getReadinessProbeIntervalMillis() {
        return readinessProbeIntervalMillis;
    }

    public void setReadinessProbeIntervalMillis(long readinessProbeIntervalMillis) {
        this.readinessProbeIntervalMillis = readinessProbeIntervalMillis;
    }

    public long getReadinessInitialDelayMillis() {
        return readinessInitialDelayMillis;
    }

    public void setReadinessInitialDelayMillis(long readinessInitialDelayMillis) {
        this.readinessInitialDelayMillis = readinessInitialDelayMillis;
    }
}
