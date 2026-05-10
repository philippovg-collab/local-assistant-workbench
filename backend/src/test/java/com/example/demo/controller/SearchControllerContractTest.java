package com.example.demo.controller;

import com.example.demo.model.DocumentBlockType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.ChunkScoreBreakdown;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSearchDebug;
import com.example.demo.model.MaterialSearchHit;
import com.example.demo.model.MaterialSearchHitNeighbor;
import com.example.demo.model.MaterialSearchResponse;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.service.MaterialService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SearchControllerContractTest {

    private MockMvc mockMvc;
    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        materialService = mock(MaterialService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SearchController(materialService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void rejectsMalformedSearchPayloadsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(materialService);
    }

    @Test
    void acceptsSearchPayloadWithFiltersNeighborsAndDebug() throws Exception {
        when(materialService.search(any())).thenReturn(new MaterialSearchResponse(
            "dispatch matrix",
            List.of(new MaterialSearchHit(
                "material-123",
                "material-123:1",
                "Dispatch matrix",
                "Dispatch matrix for contract KZ-2026-0415-ENERGY",
                1,
                3,
                DocumentBlockType.TABLE,
                94,
                0.12d,
                2.7d,
                List.of("dispatch", "matrix"),
                "/api/materials/material-123?chunkId=material-123%3A1&chunkIndex=1&page=3",
                MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                    DocumentType.CONTRACT,
                    LocalDate.parse("2026-04-15"),
                    "KZ-2026-0415-ENERGY",
                    "Dana Sarsen",
                    "Grid operations",
                    "v2",
                    "ru",
                    List.of("dispatch"),
                    SourceTrustLevel.HIGH,
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-06-30")
                )),
                List.of(new MaterialSearchHitNeighbor(
                    "material-123:0",
                    0,
                    "Overview",
                    2,
                    DocumentBlockType.NARRATIVE
                )),
                new ChunkScoreBreakdown(62, 4, 5, 20, 8, 8, 6, 0, 0, 0, 100)
            )),
            new MaterialSearchDebug(
                new RetrievalFilters(
                    "KZ-2026-0415-ENERGY",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    "Grid operations",
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    "ru",
                    List.of("dispatch"),
                    SourceTrustLevel.MEDIUM
                ),
                new RetrievalFilters(
                    "KZ-2026-0415-ENERGY",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    "Grid operations",
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    "ru",
                    List.of("dispatch"),
                    SourceTrustLevel.MEDIUM
                ),
                new RetrievalFilters(
                    "KZ-2026-0415-ENERGY",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    "Grid operations",
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    "ru",
                    List.of("dispatch"),
                    SourceTrustLevel.MEDIUM
                ),
                new com.example.demo.model.RetrievalQueryHints(
                    "KZ-2026-0415-ENERGY",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    "v2",
                    "ru",
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    "Grid operations"
                ),
                4,
                3,
                3,
                1,
                "postgres",
                "postgres",
                false,
                "search.sync_disabled",
                "sufficient",
                true,
                "hybrid-rerank-v1",
                "hybrid-rerank-v1"
            )
        ));

        mockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "dispatch matrix",
                      "filters": {
                        "documentNumber": "KZ-2026-0415-ENERGY",
                        "documentDateFrom": "2026-04-01",
                        "documentDateTo": "2026-04-30",
                        "department": "Grid operations",
                        "project": "North Upgrade",
                        "counterparty": "GridBuild LLP",
                        "businessStatus": "APPROVED",
                        "language": "ru",
                        "tags": ["dispatch"],
                        "sourceTrustMin": "MEDIUM"
                      },
                      "limit": 1,
                      "includeNeighbors": true,
                      "debug": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").value("dispatch matrix"))
            .andExpect(jsonPath("$.hits[0].chunkType").value("TABLE"))
            .andExpect(jsonPath("$.hits[0].metadata.documentNumber").value("KZ-2026-0415-ENERGY"))
            .andExpect(jsonPath("$.hits[0].neighbors[0].chunkIndex").value(0))
            .andExpect(jsonPath("$.debug.appliedFilters.sourceTrustMin").value("MEDIUM"))
            .andExpect(jsonPath("$.hits[0].scoreBreakdown.identifierBonus").value(20))
            .andExpect(jsonPath("$.debug.queryHints.versionLabel").value("v2"))
            .andExpect(jsonPath("$.debug.rerankerApplied").value(true))
            .andExpect(jsonPath("$.debug.semanticCandidateCount").value(4))
            .andExpect(jsonPath("$.debug.effectiveLexicalProvider").value("postgres"));

        verify(materialService).search(any());
    }

    @Test
    void rejectsMalformedRetrievalFiltersWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "dispatch matrix",
                      "filters": {
                        "sourceTrustMin": "BROKEN"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(materialService);
    }

    @Test
    void rejectsInvalidRetrievalFilterDateRangeWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "dispatch matrix",
                      "filters": {
                        "documentDateFrom": "2026-05-01",
                        "documentDateTo": "2026-04-01"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(materialService);
    }
}
