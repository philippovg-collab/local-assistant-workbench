package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.health")
public class HealthProperties {

    private int readinessCacheSeconds = 30;

    public int getReadinessCacheSeconds() {
        return readinessCacheSeconds;
    }

    public void setReadinessCacheSeconds(int readinessCacheSeconds) {
        this.readinessCacheSeconds = readinessCacheSeconds;
    }
}
