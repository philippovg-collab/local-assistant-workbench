package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private static final Set<String> WEAK_ADMIN_PASSWORDS = Set.of(
        "admin",
        "password",
        "secret",
        "local-admin-password"
    );
    private static final Set<String> DEV_LIKE_PROFILES = Set.of("local", "dev", "test");

    private boolean enabled = true;
    private String adminUsername = "admin";
    private String adminPassword;
    private Environment environment;

    @PostConstruct
    void validate() {
        validateForRuntime(isTestRuntime());
    }

    void validateForRuntime(boolean testRuntime) {
        if (!enabled) {
            if (!testRuntime && !hasDevLikeProfile()) {
                throw new IllegalStateException(
                    "app.security.enabled=false is allowed only for local, dev, or test runtimes"
                );
            }
            return;
        }

        if (!StringUtils.hasText(adminUsername)) {
            throw new IllegalStateException(
                "app.security.admin-username must not be blank when app.security.enabled=true"
            );
        }

        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                "app.security.admin-password must be configured when app.security.enabled=true"
            );
        }

        if (!testRuntime && WEAK_ADMIN_PASSWORDS.contains(adminPassword.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                "app.security.admin-password must not use a weak default password"
            );
        }
    }

    @Autowired(required = false)
    void setEnvironment(Environment environment) {
        this.environment = environment;
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

    public String getNormalizedAdminUsername() {
        return adminUsername == null ? "" : adminUsername.trim();
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

    private boolean hasDevLikeProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
            .map(profile -> profile.toLowerCase(Locale.ROOT))
            .anyMatch(DEV_LIKE_PROFILES::contains);
    }

    private boolean isTestRuntime() {
        return ClassUtils.isPresent("org.junit.jupiter.api.Test", getClass().getClassLoader());
    }
}
