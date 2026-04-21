package com.example.demo.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialPdfUploadPolicyResponse;
import com.example.demo.model.RechunkActiveMaterialsBatchResponse;
import com.example.demo.model.RechunkActiveMaterialsResponse;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.service.MaterialService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
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

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "version.txt",
            "text/plain",
            "Новая версия".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/materials/not-a-uuid/versions").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.invalid_id"));

        mockMvc.perform(put("/api/materials/not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Updated",
                      "content": "Updated content"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.invalid_id"));

        verifyNoInteractions(materialService);
    }

    @Test
    void exposesControlledVersionUploadEndpoint() throws Exception {
        String materialId = "00000000-0000-0000-0000-000000000123";
        when(materialService.saveUploadVersion(eq(materialId), eq("Updated policy"), any(), any(), isNull()))
            .thenReturn(new MaterialSummary(
                "00000000-0000-0000-0000-000000000456",
                "Updated policy",
                "file",
                "version.txt",
                MaterialIndexingStatus.PENDING,
                MaterialVersionState.ACTIVE,
                null,
                null,
                Instant.parse("2026-04-21T10:00:00Z"),
                14,
                "Новая версия"
            ));
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "version.txt",
            "text/plain",
            "Новая версия".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile metadata = new MockMultipartFile(
            "metadata",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
                {
                  "documentType": "POLICY",
                  "manualTags": ["grid"]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );
        ArgumentCaptor<MaterialMetadataInput> metadataCaptor = ArgumentCaptor.forClass(MaterialMetadataInput.class);

        mockMvc.perform(multipart("/api/materials/{id}/versions", materialId)
                .file(file)
                .file(metadata)
                .param("title", "Updated policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000456"))
            .andExpect(jsonPath("$.title").value("Updated policy"));

        verify(materialService).saveUploadVersion(
            eq(materialId),
            eq("Updated policy"),
            any(MultipartFile.class),
            metadataCaptor.capture(),
            isNull()
        );
        org.junit.jupiter.api.Assertions.assertEquals(DocumentType.POLICY, metadataCaptor.getValue().documentType());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("grid"), metadataCaptor.getValue().manualTags());
    }

    @Test
    void exposesMaterialEditEndpoint() throws Exception {
        String materialId = "00000000-0000-0000-0000-000000000123";
        when(materialService.editMaterial(eq(materialId), eq("Updated policy"), eq("Updated text"), any(), isNull()))
            .thenReturn(new MaterialSummary(
                "00000000-0000-0000-0000-000000000456",
                "Updated policy",
                "file",
                "policy.txt",
                MaterialIndexingStatus.PENDING,
                MaterialVersionState.ACTIVE,
                null,
                null,
                Instant.parse("2026-04-21T10:00:00Z"),
                12,
                "Updated text"
            ));
        ArgumentCaptor<MaterialMetadataInput> metadataCaptor = ArgumentCaptor.forClass(MaterialMetadataInput.class);

        mockMvc.perform(put("/api/materials/{id}", materialId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Updated policy",
                      "content": "Updated text",
                      "metadata": {
                        "documentType": "POLICY",
                        "workspaceKey": "general",
                        "documentStatus": "ACTIVE",
                        "manualTags": ["grid"]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000456"))
            .andExpect(jsonPath("$.title").value("Updated policy"));

        verify(materialService).editMaterial(
            eq(materialId),
            eq("Updated policy"),
            eq("Updated text"),
            metadataCaptor.capture(),
            isNull()
        );
        org.junit.jupiter.api.Assertions.assertEquals(DocumentType.POLICY, metadataCaptor.getValue().documentType());
        org.junit.jupiter.api.Assertions.assertEquals("general", metadataCaptor.getValue().workspaceKey());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("grid"), metadataCaptor.getValue().manualTags());
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
            8_388_608,
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
            .andExpect(jsonPath("$.maxUploadBytes").value(8_388_608))
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
