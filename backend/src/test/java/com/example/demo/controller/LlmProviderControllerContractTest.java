package com.example.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.llmprovider.LlmProviderProbeService;
import com.example.demo.llmprovider.LlmProviderService;
import com.example.demo.model.LlmProviderConfigResponse;
import com.example.demo.model.LlmProviderInput;
import com.example.demo.model.LlmProviderModelInfo;
import com.example.demo.model.LlmProviderProbeResult;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LlmProviderControllerContractTest {

    private MockMvc mockMvc;
    private LlmProviderService providerService;
    private LlmProviderProbeService probeService;

    @BeforeEach
    void setUp() {
        providerService = mock(LlmProviderService.class);
        probeService = mock(LlmProviderProbeService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new LlmProviderController(providerService, probeService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void listsProvidersWithoutSecretMaterial() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(providerService.listProviders()).thenReturn(List.of(response(providerId, true, true, false)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/llm-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(providerId))
            .andExpect(jsonPath("$[0].hasApiKey").value(true))
            .andExpect(jsonPath("$[0].apiKey").doesNotExist())
            .andExpect(jsonPath("$[0].apiKeyCiphertext").doesNotExist())
            .andExpect(content().string(not(containsString("secret-api-key"))))
            .andExpect(content().string(not(containsString("ciphertext"))));
    }

    @Test
    void createsProviderWithoutReturningSecretMaterial() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(providerService.createProvider(any())).thenReturn(response(providerId, true, false, false));

        mockMvc.perform(post("/api/llm-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Corp Provider",
                      "providerType": "OPENAI_COMPATIBLE",
                      "purpose": "CHAT_AND_EMBEDDING",
                      "baseUrl": "https://llm.internal",
                      "apiKey": "secret-api-key",
                      "defaultModel": "corp-chat",
                      "embeddingModel": "corp-embedding",
                      "expectedEmbeddingDimension": 1024
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(providerId))
            .andExpect(jsonPath("$.hasApiKey").value(true))
            .andExpect(jsonPath("$.apiKey").doesNotExist())
            .andExpect(jsonPath("$.apiKeyCiphertext").doesNotExist())
            .andExpect(content().string(not(containsString("secret-api-key"))))
            .andExpect(content().string(not(containsString("ciphertext"))));

        ArgumentCaptor<LlmProviderInput> inputCaptor = ArgumentCaptor.forClass(LlmProviderInput.class);
        verify(providerService).createProvider(inputCaptor.capture());
        assertEquals("secret-api-key", inputCaptor.getValue().apiKey());
    }

    @Test
    void updatesProviderAndPreservesExplicitClearApiKeyIntent() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(providerService.updateProvider(eq(UUID.fromString(providerId)), any()))
            .thenReturn(response(providerId, false, false, false));

        mockMvc.perform(put("/api/llm-providers/{id}", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Corp Provider",
                      "providerType": "OPENAI_COMPATIBLE",
                      "purpose": "CHAT",
                      "baseUrl": "https://llm.internal",
                      "clearApiKey": true,
                      "defaultModel": "corp-chat"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasApiKey").value(false))
            .andExpect(jsonPath("$.apiKey").doesNotExist())
            .andExpect(jsonPath("$.apiKeyCiphertext").doesNotExist());

        ArgumentCaptor<LlmProviderInput> inputCaptor = ArgumentCaptor.forClass(LlmProviderInput.class);
        verify(providerService).updateProvider(eq(UUID.fromString(providerId)), inputCaptor.capture());
        assertTrue(Boolean.TRUE.equals(inputCaptor.getValue().clearApiKey()));
    }

    @Test
    void mapsActiveDeleteConflictToStableErrorCode() throws Exception {
        String providerId = UUID.randomUUID().toString();
        doThrow(new ApplicationException(
            ErrorType.CONFLICT,
            "llm_provider.active_delete_forbidden",
            "Switch the active LLM provider to another provider or env fallback before deleting this provider"
        )).when(providerService).deleteProvider(UUID.fromString(providerId));

        mockMvc.perform(delete("/api/llm-providers/{id}", providerId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("llm_provider.active_delete_forbidden"));
    }

    @Test
    void probesProviderAndReturnsSanitizedAvailabilityShape() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(probeService.probe(UUID.fromString(providerId))).thenReturn(new LlmProviderProbeResult(
            providerId,
            LlmProviderStatus.DEGRADED,
            "2026-05-10T10:00:00Z",
            120L,
            false,
            true,
            true,
            "llm_provider.models_unsupported",
            "Provider models endpoint is unavailable or unsupported"
        ));

        mockMvc.perform(post("/api/llm-providers/{id}/probe", providerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(providerId))
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.modelsAvailable").value(false))
            .andExpect(jsonPath("$.chatAvailable").value(true))
            .andExpect(jsonPath("$.embeddingAvailable").value(true))
            .andExpect(jsonPath("$.errorCode").value("llm_provider.models_unsupported"))
            .andExpect(content().string(not(containsString("secret-api-key"))));
    }

    @Test
    void listsRemoteModelsForProvider() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(probeService.listModels(UUID.fromString(providerId))).thenReturn(List.of(
            new LlmProviderModelInfo("corp-chat"),
            new LlmProviderModelInfo("corp-embedding")
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/llm-providers/{id}/models",
                providerId
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("corp-chat"))
            .andExpect(jsonPath("$[1].name").value("corp-embedding"));
    }

    @Test
    void activatesProviderAndFallbackWithStableContracts() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(providerService.activateProvider(eq(UUID.fromString(providerId)), any()))
            .thenReturn(storedProvider(providerId, true, false));

        mockMvc.perform(post("/api/llm-providers/{id}/activate", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"CHAT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(providerId))
            .andExpect(jsonPath("$.activeChat").value(true))
            .andExpect(jsonPath("$.apiKey").doesNotExist())
            .andExpect(jsonPath("$.apiKeyCiphertext").doesNotExist());

        mockMvc.perform(post("/api/llm-providers/fallback/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"EMBEDDING\"}"))
            .andExpect(status().isNoContent());

        ArgumentCaptor<com.example.demo.model.LlmProviderActivateRequest> activationCaptor =
            ArgumentCaptor.forClass(com.example.demo.model.LlmProviderActivateRequest.class);
        verify(providerService).activateProvider(eq(UUID.fromString(providerId)), activationCaptor.capture());
        assertEquals(LlmProviderPurpose.CHAT, activationCaptor.getValue().purpose());
        verify(providerService).activateFallback(any());
    }

    @Test
    void rejectsInvalidActivationPayloadWithStableValidationCode() throws Exception {
        String providerId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/llm-providers/{id}/activate", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    @Test
    void rejectsInvalidCrudPayloadWithStableValidationCode() throws Exception {
        mockMvc.perform(post("/api/llm-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "providerType": "OPENAI_COMPATIBLE",
                      "purpose": "CHAT",
                      "baseUrl": "https://llm.internal"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.validation_failed"));
    }

    private LlmProviderConfigResponse response(
        String id,
        boolean hasApiKey,
        boolean activeChat,
        boolean activeEmbedding
    ) {
        return new LlmProviderConfigResponse(
            id,
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "https://llm.internal",
            hasApiKey,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-chat",
            "corp-embedding",
            0.2,
            600,
            1024,
            activeChat,
            activeEmbedding,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            "2026-05-10T10:00:00Z",
            "2026-05-10T10:00:00Z"
        );
    }

    private com.example.demo.llmprovider.LlmProviderConfig storedProvider(
        String id,
        boolean activeChat,
        boolean activeEmbedding
    ) {
        java.time.Instant now = java.time.Instant.parse("2026-05-10T10:00:00Z");
        return new com.example.demo.llmprovider.LlmProviderConfig(
            UUID.fromString(id),
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "https://llm.internal",
            "ciphertext-v1",
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-chat",
            "corp-embedding",
            0.2,
            600,
            1024,
            activeChat,
            activeEmbedding,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }
}
