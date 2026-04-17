package com.example.demo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.service.RagStorageHealthService;
import com.example.demo.service.RuntimeReadinessService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    private MockMvc mockMvc;
    private OcrCapabilityProvider ocrCapabilityProvider;
    private RagStorageHealthService ragStorageHealthService;
    private RuntimeReadinessService runtimeReadinessService;
    private MaterialIndexingQueueRepository indexingQueueRepository;

    @BeforeEach
    void setUp() {
        OcrProperties ocrProperties = new OcrProperties();
        ocrCapabilityProvider = org.mockito.Mockito.mock(OcrCapabilityProvider.class);
        ragStorageHealthService = org.mockito.Mockito.mock(RagStorageHealthService.class);
        runtimeReadinessService = org.mockito.Mockito.mock(RuntimeReadinessService.class);
        indexingQueueRepository = org.mockito.Mockito.mock(MaterialIndexingQueueRepository.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new HealthController(
                ocrProperties,
                ocrCapabilityProvider,
                ragStorageHealthService,
                runtimeReadinessService,
                indexingQueueRepository
            ))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void reportsHealthyCompositionUsingCachedRuntimeReadiness() throws Exception {
        when(ocrCapabilityProvider.currentCapability()).thenReturn(
            OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12)
        );
        when(ragStorageHealthService.currentHealth()).thenReturn(
            new RagStorageHealthService.StorageHealth("UP", null, "UP", null)
        );
        when(runtimeReadinessService.currentReadiness()).thenReturn(
            new RuntimeReadinessService.RuntimeReadiness(
                "UP",
                "UP",
                "UP",
                null,
                null,
                "UP",
                null,
                null,
                "2026-04-17T10:00:00Z",
                "2026-04-17T09:59:00Z",
                "2026-04-17T09:59:30Z"
            )
        );
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(1, 0, 0, Instant.parse("2026-04-17T10:05:00Z"))
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.runtimeCachedAt").value("2026-04-17T10:00:00Z"))
            .andExpect(jsonPath("$.llmLastSuccessfulProbeAt").value("2026-04-17T09:59:00Z"))
            .andExpect(jsonPath("$.embeddingLastSuccessfulProbeAt").value("2026-04-17T09:59:30Z"))
            .andExpect(jsonPath("$.indexingPendingCount").value(1))
            .andExpect(jsonPath("$.databaseStatus").value("UP"))
            .andExpect(jsonPath("$.vectorStatus").value("UP"))
            .andExpect(jsonPath("$.ocrStatus").value("UP"))
            .andExpect(jsonPath("$.ocrLanguages[0]").value("kaz"));
    }

    @Test
    void reportsDegradedWhenOcrSupportIsUnavailable() throws Exception {
        when(ocrCapabilityProvider.currentCapability()).thenReturn(
            OcrCapability.embeddedTextOnly(
                "material.ocr_unavailable",
                "Tesseract OCR binary is unavailable at 'tesseract'.",
                List.of("kaz", "rus", "eng"),
                12
            )
        );
        when(ragStorageHealthService.currentHealth()).thenReturn(
            new RagStorageHealthService.StorageHealth("UP", null, "UP", null)
        );
        when(runtimeReadinessService.currentReadiness()).thenReturn(
            new RuntimeReadinessService.RuntimeReadiness(
                "UP",
                "UP",
                "UP",
                null,
                null,
                "UP",
                null,
                null,
                "2026-04-17T10:00:00Z",
                null,
                null
            )
        );
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(0, 0, 0, null)
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.ocrStatus").value("DOWN"))
            .andExpect(jsonPath("$.ocrReasonCode").value("material.ocr_unavailable"));
    }

    @Test
    void reportsDegradedWhenRuntimeReadinessIsDown() throws Exception {
        when(ocrCapabilityProvider.currentCapability()).thenReturn(
            OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12)
        );
        when(ragStorageHealthService.currentHealth()).thenReturn(
            new RagStorageHealthService.StorageHealth("UP", null, "UP", null)
        );
        when(runtimeReadinessService.currentReadiness()).thenReturn(
            new RuntimeReadinessService.RuntimeReadiness(
                "DOWN",
                "DOWN",
                "DOWN",
                "llm.provider_unavailable",
                "Unable to reach the local LLM provider",
                "DOWN",
                "embedding.provider_unavailable",
                "Embedding readiness depends on Ollama model discovery, but the model list is unavailable.",
                "2026-04-17T10:00:00Z",
                null,
                null
            )
        );
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(0, 1, 2, Instant.parse("2026-04-17T10:15:00Z"))
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.directStatus").value("DOWN"))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"))
            .andExpect(jsonPath("$.llmReasonCode").value("llm.provider_unavailable"))
            .andExpect(jsonPath("$.embeddingReasonCode").value("embedding.provider_unavailable"))
            .andExpect(jsonPath("$.ragDegradedReasonCode").value("llm.provider_unavailable"))
            .andExpect(jsonPath("$.indexingInProgressCount").value(1))
            .andExpect(jsonPath("$.indexingFailedCount").value(2));
    }
}
