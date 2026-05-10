package com.example.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.JsonRequestSizeLimitFilter;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RequestLimitProperties;
import com.example.demo.config.SecurityConfig;
import com.example.demo.config.SecurityProperties;
import com.example.demo.service.HealthStatusService;
import com.example.demo.service.OperatorAuditService;
import com.example.demo.service.audit.OperatorAuditEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {
    AuthController.class,
    HealthController.class,
    SecurityConfigTest.BusinessEndpointProbeController.class
})
@Import({
    SecurityConfig.class,
    JsonRequestSizeLimitFilter.class,
    SecurityConfigTest.BusinessEndpointProbeController.class
})
@EnableConfigurationProperties({SecurityProperties.class, RequestLimitProperties.class, MaterialProperties.class})
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "app.security.admin-username=admin",
    "app.security.admin-password=strong-test-password-123",
    "app.request.max-json-bytes=1048576"
})
class SecurityConfigTest {

    private static final List<BusinessEndpoint> BUSINESS_ENDPOINTS = List.of(
        new BusinessEndpoint("GET", "/api/health", null),
        new BusinessEndpoint("GET", "/api/materials", null),
        new BusinessEndpoint("POST", "/api/search", "{}"),
        new BusinessEndpoint("GET", "/api/models", null),
        new BusinessEndpoint("GET", "/api/instructions", null),
        new BusinessEndpoint("GET", "/api/reference/workspaces", null),
        new BusinessEndpoint("GET", "/api/rag-projects", null),
        new BusinessEndpoint("POST", "/api/chat", "{}"),
        new BusinessEndpoint("GET", "/api/chat-runs", null)
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService healthStatusService;

    @MockBean
    private OperatorAuditService operatorAuditService;

    @Test
    void allowsLivenessWithoutAuthenticationButProtectsHealth() throws Exception {
        mockMvc.perform(get("/api/liveness"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.unauthenticated"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void rejectsUnauthenticatedBusinessApiMatrix() throws Exception {
        for (BusinessEndpoint endpoint : BUSINESS_ENDPOINTS) {
            performBusinessEndpoint(endpoint)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"))
                .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
        }
    }

    @Test
    void logsInWithJsonCredentialsAndExposesAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"strong-test-password-123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.csrfToken").isString());

        OperatorAuditEvent event = onlyAuditEvent();
        assertEquals("auth.login", event.eventType());
        assertEquals("success", event.outcome());
        assertEquals("admin", event.actor());
        assertEquals(200, event.statusCode());
    }

    @Test
    void rejectsInvalidLoginCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.invalid_credentials"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));

        OperatorAuditEvent event = onlyAuditEvent();
        assertEquals("auth.login_failed", event.eventType());
        assertEquals("failure", event.outcome());
        assertEquals("admin", event.actor());
        assertEquals(401, event.statusCode());
        assertEquals("auth.invalid_credentials", event.failureCode());
    }

    @Test
    void rejectsAuthStateChangesWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"strong-test-password-123"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("auth.forbidden"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));

        mockMvc.perform(post("/api/auth/logout").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("auth.forbidden"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void logsOutWithCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.csrfToken").isString());

        OperatorAuditEvent event = onlyAuditEvent();
        assertEquals("auth.logout", event.eventType());
        assertEquals("success", event.outcome());
        assertEquals("admin", event.actor());
        assertEquals(200, event.statusCode());
    }

    @Test
    void rejectsUnsafeAdminRequestsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/search")
                .with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("auth.forbidden"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void rejectsOversizedJsonBeforeControllerHandling() throws Exception {
        String payload = "{\"username\":\"admin\",\"password\":\"" + "x".repeat(1_048_600) + "\"}";

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("request.payload_too_large"))
            .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
    }

    @Test
    void rejectsRoleUserForBusinessApiMatrix() throws Exception {
        for (BusinessEndpoint endpoint : BUSINESS_ENDPOINTS) {
            performBusinessEndpoint(endpoint, user("user").roles("USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.forbidden"))
                .andExpect(jsonPath("$.requestId").value(matchesPattern("^[0-9a-f\\-]{36}$")));
        }
    }

    @Test
    void allowsAdminForBusinessApiMatrix() throws Exception {
        when(healthStatusService.currentHealth()).thenReturn(null);

        for (BusinessEndpoint endpoint : BUSINESS_ENDPOINTS) {
            performBusinessEndpoint(endpoint, user("admin").roles("ADMIN"))
                .andExpect(status().isOk());
        }
    }

    private ResultActions performBusinessEndpoint(BusinessEndpoint endpoint) throws Exception {
        return performBusinessEndpoint(endpoint, null);
    }

    private ResultActions performBusinessEndpoint(
        BusinessEndpoint endpoint,
        RequestPostProcessor principal
    ) throws Exception {
        MockHttpServletRequestBuilder request = switch (endpoint.method()) {
            case "GET" -> get(endpoint.path());
            case "POST" -> post(endpoint.path());
            default -> throw new IllegalArgumentException("Unsupported method: " + endpoint.method());
        };
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        if (endpoint.isUnsafe()) {
            request.with(csrf());
        }
        if (principal != null) {
            request.with(principal);
        }
        return mockMvc.perform(request);
    }

    private OperatorAuditEvent onlyAuditEvent() {
        ArgumentCaptor<OperatorAuditEvent> captor = ArgumentCaptor.forClass(OperatorAuditEvent.class);
        verify(operatorAuditService, times(1)).record(captor.capture());
        return captor.getValue();
    }

    private record BusinessEndpoint(String method, String path, String body) {

        boolean isUnsafe() {
            return !"GET".equals(method);
        }
    }

    @RestController
    public static class BusinessEndpointProbeController {

        @GetMapping({
            "/api/materials",
            "/api/models",
            "/api/instructions",
            "/api/reference/workspaces",
            "/api/rag-projects",
            "/api/chat-runs"
        })
        Map<String, String> read() {
            return Map.of("status", "ok");
        }

        @PostMapping({
            "/api/search",
            "/api/chat"
        })
        Map<String, String> write() {
            return Map.of("status", "ok");
        }
    }
}
