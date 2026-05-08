package com.example.demo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.JsonRequestSizeLimitFilter;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RequestLimitProperties;
import com.example.demo.config.SecurityConfig;
import com.example.demo.config.SecurityProperties;
import com.example.demo.model.MaterialListResponse;
import com.example.demo.service.ChatRunQueryService;
import com.example.demo.service.HealthStatusService;
import com.example.demo.service.MaterialService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    AuthController.class,
    HealthController.class,
    MaterialController.class,
    ChatRunQueryController.class
})
@Import({SecurityConfig.class, JsonRequestSizeLimitFilter.class})
@EnableConfigurationProperties({SecurityProperties.class, RequestLimitProperties.class, MaterialProperties.class})
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "app.security.admin-username=admin",
    "app.security.admin-password=secret",
    "app.request.max-json-bytes=1048576"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaterialService materialService;

    @MockBean
    private ChatRunQueryService chatRunQueryService;

    @MockBean
    private HealthStatusService healthStatusService;

    @Test
    void allowsLivenessWithoutAuthenticationButProtectsHealth() throws Exception {
        mockMvc.perform(get("/api/liveness"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void rejectsUnauthenticatedApiRequests() throws Exception {
        mockMvc.perform(get("/api/materials"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void logsInWithJsonCredentialsAndExposesAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"secret"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.csrfToken").isString());
    }

    @Test
    void rejectsInvalidLoginCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
    }

    @Test
    void rejectsUnsafeAuthenticatedRequestsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/materials")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Note","content":"Body"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("auth.forbidden"));
    }

    @Test
    void rejectsOversizedJsonBeforeControllerHandling() throws Exception {
        String payload = "{\"username\":\"admin\",\"password\":\"" + "x".repeat(1_048_600) + "\"}";

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("request.payload_too_large"));
    }

    @Test
    void allowsAuthenticatedAccessToRegularApiEndpoints() throws Exception {
        when(materialService.listSummariesPage(null, null, null))
            .thenReturn(new MaterialListResponse(List.of(), 0, 0, 100, false));

        mockMvc.perform(get("/api/materials").with(user("user").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void requiresAdminRoleForChatTraceEndpoints() throws Exception {
        mockMvc.perform(get("/api/chat-runs").with(user("user").roles("USER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("auth.forbidden"));

        when(chatRunQueryService.listRuns(null)).thenReturn(List.of());
        mockMvc.perform(get("/api/chat-runs").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
