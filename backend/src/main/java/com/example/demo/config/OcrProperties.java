package com.example.demo.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    private boolean enabled = true;
    private String binaryPath = "tesseract";
    private String languages = "kaz+rus+eng";
    private int timeoutSeconds = 20;
    private int maxPages = 12;
    private int renderDpi = 200;
    private int minTextThreshold = 5;
    private long maxRenderedImageBytes = 15_000_000L;
    private long maxTempFileBytes = 15_000_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBinaryPath() {
        return binaryPath;
    }

    public void setBinaryPath(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public List<String> languageList() {
        return Arrays.stream(languages.split("\\+"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getRenderDpi() {
        return renderDpi;
    }

    public void setRenderDpi(int renderDpi) {
        this.renderDpi = renderDpi;
    }

    public int getMinTextThreshold() {
        return minTextThreshold;
    }

    public void setMinTextThreshold(int minTextThreshold) {
        this.minTextThreshold = minTextThreshold;
    }

    public long getMaxTempFileBytes() {
        return maxTempFileBytes;
    }

    public void setMaxTempFileBytes(long maxTempFileBytes) {
        this.maxTempFileBytes = maxTempFileBytes;
    }

    public long getMaxRenderedImageBytes() {
        return maxRenderedImageBytes;
    }

    public void setMaxRenderedImageBytes(long maxRenderedImageBytes) {
        this.maxRenderedImageBytes = maxRenderedImageBytes;
    }
}
