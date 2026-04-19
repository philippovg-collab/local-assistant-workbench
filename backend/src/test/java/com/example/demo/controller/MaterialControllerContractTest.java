package com.example.demo.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.MaterialPdfUploadPolicyResponse;
import com.example.demo.model.RechunkActiveMaterialsBatchResponse;
import com.example.demo.model.RechunkActiveMaterialsResponse;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.service.MaterialService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaterialControllerContractTest {

    private MockMvc mockMvc;
    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        materialService = mock(MaterialService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MaterialController(materialService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void rejectsInvalidMaterialIdsBeforeTheyReachTheService() throws Exception {
        mockMvc.perform(delete("/api/materials/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.invalid_id"));

        verifyNoInteractions(materialService);
    }

    @Test
    void rejectsMalformedTextMaterialPayloadsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(materialService);
    }

    @Test
    void returnsUploadPolicyFromTheService() throws Exception {
        when(materialService.getUploadPolicy()).thenReturn(new MaterialUploadPolicyResponse(
            2_000_000,
            List.of("txt", "docx", "xlsx", "pptx", "pdf"),
            List.of(
                "text/plain",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/pdf"
            ),
            true,
            new MaterialPdfUploadPolicyResponse(
                true,
                false,
                "embedded_text_only",
                "material.ocr_unavailable",
                "OCR runtime is unavailable",
                List.of("kaz", "rus", "eng"),
                12
            )
        ));

        mockMvc.perform(get("/api/materials/policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxUploadBytes").value(2_000_000))
            .andExpect(jsonPath("$.acceptedExtensions[0]").value("txt"))
            .andExpect(jsonPath("$.acceptedExtensions", hasItems("xlsx", "pptx")))
            .andExpect(jsonPath("$.pdf.mode").value("embedded_text_only"))
            .andExpect(jsonPath("$.pdf.ocrReasonCode").value("material.ocr_unavailable"));

        verify(materialService).getUploadPolicy();
    }

    @Test
    void exposesRechunkActiveEndpoint() throws Exception {
        when(materialService.rechunkActiveMaterials()).thenReturn(new RechunkActiveMaterialsResponse(3, 2, 1, 1));

        mockMvc.perform(post("/api/materials/rechunk-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeCount").value(3))
            .andExpect(jsonPath("$.scheduledCount").value(2))
            .andExpect(jsonPath("$.alreadyCurrentCount").value(1))
            .andExpect(jsonPath("$.legacyBestEffortCount").value(1));

        verify(materialService).rechunkActiveMaterials();
    }

    @Test
    void exposesRechunkActiveBatchEndpoint() throws Exception {
        when(materialService.rechunkActiveMaterialsBatch(any())).thenReturn(new RechunkActiveMaterialsBatchResponse(
            12,
            5,
            2,
            3,
            1,
            "cursor-2",
            List.of(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000003"
            )
        ));

        mockMvc.perform(post("/api/materials/rechunk-active/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "limit": 5,
                      "dryRun": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalActive").value(12))
            .andExpect(jsonPath("$.scanned").value(5))
            .andExpect(jsonPath("$.scheduled").value(2))
            .andExpect(jsonPath("$.alreadyCurrent").value(3))
            .andExpect(jsonPath("$.legacyBestEffort").value(1))
            .andExpect(jsonPath("$.nextCursor").value("cursor-2"))
            .andExpect(jsonPath("$.materialIds[0]").value("00000000-0000-0000-0000-000000000001"))
            .andExpect(jsonPath("$.materialIds[1]").value("00000000-0000-0000-0000-000000000003"));

        verify(materialService).rechunkActiveMaterialsBatch(any());
    }
}
