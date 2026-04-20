package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.request")
public class RequestLimitProperties {

    private int maxJsonBytes = 1_048_576;

    public int getMaxJsonBytes() {
        return maxJsonBytes;
    }

    public void setMaxJsonBytes(int maxJsonBytes) {
        this.maxJsonBytes = maxJsonBytes;
    }
}
