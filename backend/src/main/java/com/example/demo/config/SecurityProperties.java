package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private boolean enabled = true;
    private String adminUsername = "admin";
    private String adminPassword;

    @PostConstruct
    void validate() {
        if (enabled && !StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                "app.security.admin-password must be configured when app.security.enabled=true"
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
