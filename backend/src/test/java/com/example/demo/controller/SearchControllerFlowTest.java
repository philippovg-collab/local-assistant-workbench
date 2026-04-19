package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.infrastructure.material.DocumentBlockConfidence;
import com.example.demo.infrastructure.material.DocumentBlockType;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.service.HybridChunkRanker;
import com.example.demo.service.MaterialContentSupport;
import com.example.demo.service.MaterialIngestionService;
import com.example.demo.service.MaterialQueryService;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.MaterialService;
import com.example.demo.service.QualityLayerHealthService;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

class SearchControllerFlowTest {

    private final MaterialProperties materialProperties = new MaterialProperties();
    private final RagProperties ragProperties = new RagProperties();
    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();

    private InMemoryMaterialRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMaterialRepository();
        seedSearchableMaterials();

        MaterialService materialService = createMaterialService(RolloutProperties.enabledForTests());
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SearchController(materialService))
            .setControllerAdvice(new ApiExceptionHandler(materialProperties))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void returnsChunkLevelSearchHitsWithMetadataFiltersNeighborsAndDebug() throws Exception {
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
            .andExpect(jsonPath("$.hits[0].title").value("North dispatch contract"))
            .andExpect(jsonPath("$.hits[0].chunkIndex").value(1))
            .andExpect(jsonPath("$.hits[0].chunkType").value("TABLE"))
            .andExpect(jsonPath("$.hits[0].metadata.documentNumber").value("KZ-2026-0415-ENERGY"))
            .andExpect(jsonPath("$.hits[0].metadata.project").value("North Upgrade"))
            .andExpect(jsonPath("$.hits[0].metadata.sourceTrust").value("HIGH"))
            .andExpect(jsonPath("$.hits[0].neighbors[0].chunkIndex").value(0))
            .andExpect(jsonPath("$.hits[0].neighbors[1].chunkIndex").value(2))
            .andExpect(jsonPath("$.debug.appliedFilters.documentNumber").value("KZ-2026-0415-ENERGY"))
            .andExpect(jsonPath("$.debug.appliedFilters.sourceTrustMin").value("MEDIUM"))
            .andExpect(jsonPath("$.debug.rankedHitCount").value(1))
            .andExpect(jsonPath("$.debug.effectiveLexicalProvider").value("postgres"));
    }

    @Test
    void returnsConflictWhenSearchApiRolloutIsDisabled() throws Exception {
        MaterialService disabledMaterialService = createMaterialService(new RolloutProperties());
        MockMvc disabledMockMvc = MockMvcBuilders
            .standaloneSetup(new SearchController(disabledMaterialService))
            .setControllerAdvice(new ApiExceptionHandler(materialProperties))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();

        disabledMockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "dispatch matrix"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("search.api_disabled"));
    }

    @Test
    void exposesSuppressedMetadataFiltersInSearchDebugWhenRolloutIsDisabled() throws Exception {
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setMetadataFiltersV1(false);
        rolloutProperties.setQueryHintsV1(true);
        MaterialService disabledFiltersMaterialService = createMaterialService(rolloutProperties);
        MockMvc disabledFiltersMockMvc = MockMvcBuilders
            .standaloneSetup(new SearchController(disabledFiltersMaterialService))
            .setControllerAdvice(new ApiExceptionHandler(materialProperties))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();

        disabledFiltersMockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "dispatch matrix",
                      "filters": {
                        "department": "South operations"
                      },
                      "debug": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hits[0].title").exists())
            .andExpect(jsonPath("$.debug.manualFilters.department").value("South operations"))
            .andExpect(jsonPath("$.debug.activeRolloutFlags.metadataFiltersV1").value(false))
            .andExpect(jsonPath("$.debug.activeRolloutFlags.queryHintsV1").value(true))
            .andExpect(jsonPath("$.debug.suppressedCapabilities[0]").value("metadata-filters-v1"))
            .andExpect(jsonPath("$.debug.suppressedCapabilities[1]").value("query-hints-v1"));
    }

    @Test
    void rejectsBlankQueriesFromRealSearchService() throws Exception {
        mockMvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("search.invalid_query"));
    }

    private void seedSearchableMaterials() {
        saveReadyStructuredMaterial(
            "North dispatch contract",
            "north-dispatch-lineage",
            List.of(
                new StoredMaterialChunk(
                    0,
                    "North Upgrade dispatch overview and context.",
                    List.of("north", "upgrade", "dispatch"),
                    1,
                    "structured-v1",
                    false,
                    DocumentBlockType.NARRATIVE,
                    List.of("dispatch-overview"),
                    List.of("Dispatch overview"),
                    null,
                    null,
                    DocumentBlockConfidence.HIGH
                ),
                new StoredMaterialChunk(
                    1,
                    "Dispatch matrix for contract KZ-2026-0415-ENERGY approved for April 2026.",
                    List.of("dispatch", "matrix", "contract", "approved"),
                    2,
                    "structured-v1",
                    false,
                    DocumentBlockType.TABLE,
                    List.of("dispatch-overview", "dispatch-matrix"),
                    List.of("Dispatch overview", "Dispatch matrix"),
                    "table-1",
                    null,
                    DocumentBlockConfidence.HIGH
                ),
                new StoredMaterialChunk(
                    2,
                    "Appendix B contains archival remarks only.",
                    List.of("appendix", "archival"),
                    3,
                    "structured-v1",
                    false,
                    DocumentBlockType.APPENDIX,
                    List.of("appendix-b"),
                    List.of("Appendix B"),
                    null,
                    null,
                    DocumentBlockConfidence.HIGH
                )
            ),
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch", "grid"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            ))
        );
        saveReadyStructuredMaterial(
            "South dispatch memo",
            "south-dispatch-lineage",
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for the southern district is still draft.",
                List.of("dispatch", "matrix", "draft"),
                1,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                List.of("dispatch-matrix"),
                List.of("Dispatch matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-12"),
                "SOUTH-2026-0412",
                "Aruzhan Imanova",
                "South operations",
                "v1",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.LOW,
                "South Upgrade",
                "SouthGrid LLP",
                "DRAFT",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30")
            ))
        );
    }

    private MaterialService createMaterialService(RolloutProperties rolloutProperties) {
        MaterialContentSupport contentSupport = new MaterialContentSupport(materialProperties);
        MaterialRetrievalService retrievalService = new MaterialRetrievalService(
            repository,
            repository,
            repository,
            TestLexicalRoutingSupport.productionRouter(repository, ragProperties, List.of(repository)),
            embeddingClient,
            ragProperties,
            new HybridChunkRanker(),
            new com.example.demo.service.ChunkReranker(contentSupport),
            contentSupport,
            new com.example.demo.service.RetrievalQueryHintExtractor(),
            (query, productionProvider, productionMatches, limit) -> {
            },
            new com.example.demo.service.AnswerModePostProcessor(contentSupport),
            rolloutProperties,
            QualityLayerHealthService.noop(rolloutProperties)
        );
        return new MaterialService(
            mock(MaterialQueryService.class),
            mock(MaterialIngestionService.class),
            retrievalService
        );
    }

    private void saveReadyStructuredMaterial(
        String title,
        String sourceKey,
        List<StoredMaterialChunk> rawChunks,
        MaterialMetadataSnapshot metadata
    ) {
        Instant timestamp = Instant.parse("2026-04-17T10:00:00Z");
        String normalizedContent = rawChunks.stream()
            .map(StoredMaterialChunk::text)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            "text",
            null,
            "text/plain",
            normalizedContent,
            normalizedContent,
            UUID.randomUUID().toString(),
            sourceKey,
            "structured-v1",
            false,
            null,
            rawChunks,
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            timestamp,
            timestamp,
            metadata
        );

        repository.save(record, "structured-v1", rawChunks, List.of());
        repository.markIndexingReady(
            record.id(),
            rawChunks.stream()
                .map(chunk -> new StoredEmbeddedMaterialChunk(
                    chunk.index(),
                    chunk.text(),
                    chunk.page(),
                    chunk.extractor(),
                    Boolean.TRUE.equals(chunk.ocrUsed()),
                    embeddingClient.embed(chunk.text()),
                    chunk.chunkType(),
                    chunk.sectionPath(),
                    chunk.headingTrail(),
                    chunk.tableId(),
                    chunk.slideId(),
                    chunk.parserConfidence()
                ))
                .toList(),
            MaterialIndexingStatus.READY,
            null,
            null,
            timestamp
        );
    }
}
