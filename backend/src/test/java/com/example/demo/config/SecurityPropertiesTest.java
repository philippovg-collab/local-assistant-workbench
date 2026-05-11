package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityPropertiesTest {

    @Test
    void rejectsBlankUsernameWhenSecurityIsEnabled() {
        SecurityProperties properties = properties("   ", "strong-password-123");

        assertThrows(IllegalStateException.class, () -> properties.validateForRuntime(false));
    }

    @Test
    void rejectsMissingPasswordWhenSecurityIsEnabled() {
        SecurityProperties properties = properties("admin", "   ");

        assertThrows(IllegalStateException.class, () -> properties.validateForRuntime(false));
    }

    @Test
    void rejectsWeakAdminPasswordsOutsideTestRuntimeAndDevProfiles() {
        for (String weakPassword : new String[] {
            "admin",
            "password",
            "secret",
            "local-admin-password",
            "change-me",
            "change-me-admin",
            "ragstudio",
            "replace-with-strong-admin-password"
        }) {
            SecurityProperties properties = properties("admin", weakPassword);

            assertThrows(IllegalStateException.class, () -> properties.validateForRuntime(false));
        }
    }

    @Test
    void allowsWeakAdminPasswordsOnlyForTestRuntime() {
        SecurityProperties properties = properties("admin", "secret");

        assertDoesNotThrow(() -> properties.validateForRuntime(true));
    }

    @Test
    void allowsWeakAdminPasswordsForDevLikeProfiles() {
        SecurityProperties properties = properties("admin", "change-me-admin");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        properties.setEnvironment(environment);

        assertDoesNotThrow(() -> properties.validateForRuntime(false));
    }

    @Test
    void rejectsSecurityDisabledOutsideLocalDevOrTestRuntime() {
        SecurityProperties properties = properties("admin", "strong-password-123");
        properties.setEnabled(false);

        assertThrows(IllegalStateException.class, () -> properties.validateForRuntime(false));
        assertDoesNotThrow(() -> properties.validateForRuntime(true));
    }

    private SecurityProperties properties(String username, String password) {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        properties.setAdminUsername(username);
        properties.setAdminPassword(password);
        return properties;
    }
}
