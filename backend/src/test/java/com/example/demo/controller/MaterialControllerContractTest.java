package com.example.demo.controller;

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
            List.of("txt", "docx", "pdf"),
            List.of("text/plain", "application/pdf"),
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
            .andExpect(jsonPath("$.pdf.mode").value("embedded_text_only"))
            .andExpect(jsonPath("$.pdf.ocrReasonCode").value("material.ocr_unavailable"));

        verify(materialService).getUploadPolicy();
    }
}
