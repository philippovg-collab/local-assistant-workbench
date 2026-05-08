package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.embeddings")
public class EmbeddingProperties {

    private String baseUrl;
    private String apiKey;
    private String model = "nomic-embed-text";
    private int timeoutSeconds = 60;
    private int expectedDimension = 768;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getExpectedDimension() {
        return expectedDimension;
    }

    public void setExpectedDimension(int expectedDimension) {
        this.expectedDimension = expectedDimension;
    }
}
