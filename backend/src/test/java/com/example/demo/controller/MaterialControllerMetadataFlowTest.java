package com.example.demo.controller;

import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.port.DocumentTextExtractor;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.OcrCapabilityService;
import com.example.demo.infrastructure.material.OcrClient;
import com.example.demo.infrastructure.material.PdfDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.PlainTextDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.RoutingDocumentTextExtractor;
import com.example.demo.infrastructure.material.TesseractRuntimeProbe;
import com.example.demo.infrastructure.material.TikaDocumentTextExtractor;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.HybridChunkRanker;
import com.example.demo.service.LexicalSearchStrategy;
import com.example.demo.service.MaterialContentSupport;
import com.example.demo.service.MaterialIndexingService;
import com.example.demo.service.MaterialIngestionService;
import com.example.demo.service.MaterialMetadataResolver;
import com.example.demo.service.MaterialQueryService;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.MaterialSearchSyncLifecycleService;
import com.example.demo.service.MaterialService;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaterialControllerMetadataFlowTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MaterialService materialService = createService();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MaterialController(materialService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void createsTextMaterialsWithManualMetadataWithoutDocker() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Grid policy",
                      "content": "Регламент резервирования мощности действует до конца 2026 года.",
                      "metadata": {
                        "documentType": "POLICY",
                        "knowledgeDocumentClass": "REGULATIONS",
                        "documentDate": "2026-04-17",
                        "documentNumber": "POL-2026-17",
                        "author": "Ops lead",
                        "department": "Grid operations",
                        "versionLabel": "v3.2",
                        "language": "ru",
                        "tags": ["policy", "grid"],
                        "sourceTrust": "HIGH",
                        "project": "North Upgrade",
                        "workspaceKey": "north-upgrade",
                        "counterparty": "GridBuild LLP",
                        "businessStatus": "APPROVED",
                        "periodStart": "2026-04-01",
                        "periodEnd": "2026-12-31"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.documentType").value("POLICY"))
            .andExpect(jsonPath("$.metadata.knowledgeDocumentClass").value("regulations"))
            .andExpect(jsonPath("$.metadata.documentNumber").value("POL-2026-17"))
            .andExpect(jsonPath("$.metadata.workspaceKey").value("north-upgrade"))
            .andExpect(jsonPath("$.metadata.tags", hasItem("policy")))
            .andExpect(jsonPath("$.metadata.sourceTrust").value("HIGH"))
            .andExpect(jsonPath("$.metadata.documentStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentType").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.knowledgeDocumentClass").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.workspaceKey").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.sourceTrust").value("MANUAL"));

        mockMvc.perform(get("/api/materials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(100))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items[0].title").value("Grid policy"))
            .andExpect(jsonPath("$.items[0].metadata.documentType").value("POLICY"))
            .andExpect(jsonPath("$.items[0].metadata.provenance.fieldOrigins.documentType").value("MANUAL"));
    }

    @Test
    void infersMetadataForTextMaterialsWithoutDocker() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Contract KZ-2026-0415-ENERGY 2026-04-15 v2 ru",
                      "content": "Автор: Dana Sarsen\\nПодразделение: Grid operations\\nПроект: North Upgrade\\nКонтрагент: GridBuild LLP\\nСтатус: APPROVED\\nПериод: 01.04.2026 - 30.06.2026\\nОсновные условия договора."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.documentType").value("CONTRACT"))
            .andExpect(jsonPath("$.metadata.knowledgeDocumentClass").value("contracts"))
            .andExpect(jsonPath("$.metadata.documentNumber").value("KZ-2026-0415-ENERGY"))
            .andExpect(jsonPath("$.metadata.author").value("Dana Sarsen"))
            .andExpect(jsonPath("$.metadata.department").value("Grid operations"))
            .andExpect(jsonPath("$.metadata.versionLabel").value("v2"))
            .andExpect(jsonPath("$.metadata.language").value("ru"))
            .andExpect(jsonPath("$.metadata.languageCode").value("RU"))
            .andExpect(jsonPath("$.metadata.workspaceKey").value("general"))
            .andExpect(jsonPath("$.metadata.documentStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentType").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.knowledgeDocumentClass").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.workspaceKey").value("DEFAULT"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.author").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.sourceTrust").value("DEFAULT"))
            .andExpect(jsonPath("$.metadata.provenance.fieldConfidence.documentType").exists())
            .andExpect(jsonPath("$.metadata.provenance.fieldConfidence.author").exists());
    }

    @Test
    void acceptsEmptyOptionalMetadataAndKeepsAutoFillAvailableWithoutDocker() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Plain note",
                      "content": "Plain working note.",
                      "metadata": {
                        "documentNumber": "   ",
                        "author": "",
                        "department": " ",
                        "versionLabel": "",
                        "language": " ",
                        "tags": ["", "  "],
                        "project": "",
                        "workspaceKey": " ",
                        "counterparty": "",
                        "businessStatus": " "
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.documentType").value("OTHER"))
            .andExpect(jsonPath("$.metadata.knowledgeDocumentClass").value("other"))
            .andExpect(jsonPath("$.metadata.tags", hasSize(0)))
            .andExpect(jsonPath("$.metadata.sourceTrust").value("UNKNOWN"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentType").value("DEFAULT"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.knowledgeDocumentClass").value("DEFAULT"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.sourceTrust").value("DEFAULT"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentNumber").doesNotExist())
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.tags").doesNotExist());
    }

    @Test
    void rejectsInvalidManualMetadataPeriodRangeWithoutPersistingMaterial() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Broken period",
                      "content": "Valid content with invalid manual metadata period.",
                      "metadata": {
                        "periodStart": "2026-12-31",
                        "periodEnd": "2026-01-01"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        mockMvc.perform(get("/api/materials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void acceptsMultipartMetadataAndKeepsManualFieldsOverHintsWithoutDocker() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract-KZ-2026-0415-ENERGY-v2-ru.txt",
            "text/plain",
            """
                Автор: Dana Sarsen
                Подразделение: Grid operations
                Версия: 2
                Основные условия договора и график поставки.
                """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile metadata = new MockMultipartFile(
            "metadata",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
                {
                  "documentType": "POLICY",
                  "author": "Manual owner",
                  "tags": ["grid", "policy"],
                  "workspaceKey": "manual-policy-space"
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/materials/upload")
                .file(file)
                .file(metadata))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("contract-KZ-2026-0415-ENERGY-v2-ru.txt"))
            .andExpect(jsonPath("$.metadata.documentType").value("POLICY"))
            .andExpect(jsonPath("$.metadata.knowledgeDocumentClass").value("regulations"))
            .andExpect(jsonPath("$.metadata.author").value("Manual owner"))
            .andExpect(jsonPath("$.metadata.department").value("Grid operations"))
            .andExpect(jsonPath("$.metadata.versionLabel").value("v2"))
            .andExpect(jsonPath("$.metadata.workspaceKey").value("manual-policy-space"))
            .andExpect(jsonPath("$.metadata.tags", hasItem("grid")))
            .andExpect(jsonPath("$.metadata.sourceTrust").value("UNKNOWN"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentType").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.author").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.knowledgeDocumentClass").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.workspaceKey").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.department").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.versionLabel").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.sourceTrust").value("DEFAULT"));
    }

    @Test
    void versionUploadPreservesStoredMetadataWhenOverrideIsOmittedWithoutDocker() throws Exception {
        String id = createMaterialWithStoredMetadata();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "grid-policy-v2.txt",
            "text/plain",
            "Вторая редакция документа без metadata override.".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/materials/{id}/versions", id)
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.documentType").value("POLICY"))
            .andExpect(jsonPath("$.metadata.documentStatus").value("DRAFT"))
            .andExpect(jsonPath("$.metadata.documentNumber").value("POL-42"))
            .andExpect(jsonPath("$.metadata.languageCode").value("RU"))
            .andExpect(jsonPath("$.metadata.documentDate", hasSize(3)))
            .andExpect(jsonPath("$.metadata.documentDate[0]").value(2026))
            .andExpect(jsonPath("$.metadata.documentDate[1]").value(4))
            .andExpect(jsonPath("$.metadata.documentDate[2]").value(17))
            .andExpect(jsonPath("$.metadata.author").value("Stored owner"))
            .andExpect(jsonPath("$.metadata.department").value("Grid operations"))
            .andExpect(jsonPath("$.metadata.versionLabel").value("v1"))
            .andExpect(jsonPath("$.metadata.sourceTrust").value("HIGH"))
            .andExpect(jsonPath("$.metadata.counterparty").value("GridBuild LLP"))
            .andExpect(jsonPath("$.metadata.businessStatus").value("APPROVED"))
            .andExpect(jsonPath("$.metadata.periodStart", hasSize(3)))
            .andExpect(jsonPath("$.metadata.periodStart[0]").value(2026))
            .andExpect(jsonPath("$.metadata.periodStart[1]").value(4))
            .andExpect(jsonPath("$.metadata.periodStart[2]").value(1))
            .andExpect(jsonPath("$.metadata.periodEnd", hasSize(3)))
            .andExpect(jsonPath("$.metadata.periodEnd[0]").value(2026))
            .andExpect(jsonPath("$.metadata.periodEnd[1]").value(6))
            .andExpect(jsonPath("$.metadata.periodEnd[2]").value(30))
            .andExpect(jsonPath("$.metadata.manualTags", hasItem("manual-grid")));
    }

    @Test
    void versionUploadAppliesPartialOverrideWithoutWipingStoredMetadataWithoutDocker() throws Exception {
        String id = createMaterialWithStoredMetadata();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "grid-policy-v2.txt",
            "text/plain",
            """
                Автор: Resolver owner
                Подразделение: Resolver department
                Вторая редакция документа.
                """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile metadata = new MockMultipartFile(
            "metadata",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
                {
                  "documentType": "REPORT",
                  "documentNumber": "REP-99",
                  "manualTags": ["override"]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/materials/{id}/versions", id)
                .file(file)
                .file(metadata))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.documentType").value("REPORT"))
            .andExpect(jsonPath("$.metadata.documentStatus").value("DRAFT"))
            .andExpect(jsonPath("$.metadata.documentNumber").value("REP-99"))
            .andExpect(jsonPath("$.metadata.author").value("Stored owner"))
            .andExpect(jsonPath("$.metadata.department").value("Grid operations"))
            .andExpect(jsonPath("$.metadata.versionLabel").value("v1"))
            .andExpect(jsonPath("$.metadata.sourceTrust").value("HIGH"))
            .andExpect(jsonPath("$.metadata.counterparty").value("GridBuild LLP"))
            .andExpect(jsonPath("$.metadata.businessStatus").value("APPROVED"))
            .andExpect(jsonPath("$.metadata.periodStart", hasSize(3)))
            .andExpect(jsonPath("$.metadata.periodStart[0]").value(2026))
            .andExpect(jsonPath("$.metadata.periodStart[1]").value(4))
            .andExpect(jsonPath("$.metadata.periodStart[2]").value(1))
            .andExpect(jsonPath("$.metadata.periodEnd", hasSize(3)))
            .andExpect(jsonPath("$.metadata.periodEnd[0]").value(2026))
            .andExpect(jsonPath("$.metadata.periodEnd[1]").value(6))
            .andExpect(jsonPath("$.metadata.periodEnd[2]").value(30))
            .andExpect(jsonPath("$.metadata.manualTags", hasSize(1)))
            .andExpect(jsonPath("$.metadata.manualTags", hasItem("override")));
    }

    private String createMaterialWithStoredMetadata() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Grid policy",
                      "content": "Первая редакция документа.",
                      "metadata": {
                        "documentType": "POLICY",
                        "workspaceKey": "general",
                        "documentStatus": "DRAFT",
                        "documentNumber": "POL-42",
                        "languageCode": "RU",
                        "manualTags": ["manual-grid", "retained"],
                        "periodStart": "2026-04-01",
                        "periodEnd": "2026-06-30",
                        "documentDate": "2026-04-17",
                        "author": "Stored owner",
                        "department": "Grid operations",
                        "versionLabel": "v1",
                        "sourceTrust": "HIGH",
                        "project": "North Upgrade",
                        "counterparty": "GridBuild LLP",
                        "businessStatus": "APPROVED"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = JSON_MAPPER.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private MaterialService createService() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialProperties properties = new MaterialProperties();
        MaterialFormatRegistry formatRegistry = new MaterialFormatRegistry();
        OcrProperties ocrProperties = new OcrProperties();
        OcrClient ocrClient = (imagePath, pageNumber) -> "OCR fallback text for page " + pageNumber;
        com.example.demo.infrastructure.material.OcrCapabilityService ocrCapabilityService =
            new com.example.demo.infrastructure.material.OcrCapabilityService(
            ocrProperties,
            (binaryPath, timeoutSeconds) -> new TesseractRuntimeProbe.CommandResult(
                0,
                """
                List of available languages in "/tmp/tessdata" (3):
                kaz
                rus
                eng
                """,
                "",
                false
            )
        );
        DocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new PlainTextDocumentExtractionStrategy(formatRegistry),
            new PdfDocumentExtractionStrategy(formatRegistry, ocrProperties, ocrClient, ocrCapabilityService),
            new TikaDocumentTextExtractor(properties, formatRegistry)
        ));
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        RagProperties ragProperties = new RagProperties();
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();
        MaterialIndexingService indexingService = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            new DeterministicEmbeddingClient(),
            properties,
            lifecycleService,
            Runnable::run
        );
        return new MaterialService(
            new MaterialQueryService(
                repository,
                repository,
                properties,
                formatRegistry,
                ocrCapabilityService,
                contentSupport,
                lifecycleService,
                indexingService,
                afterCommitExecutor,
                new MaterialMetadataResolver(new com.example.demo.support.NoopReferenceDataRepository())
            ),
            new MaterialIngestionService(
                repository,
                repository,
                extractor,
                properties,
                contentSupport,
                new MaterialMetadataResolver(new com.example.demo.support.NoopReferenceDataRepository()),
                lifecycleService,
                indexingService,
                afterCommitExecutor
            ),
            TestMaterialServices.retrievalService(
                repository,
                repository,
                repository,
                TestLexicalRoutingSupport.productionRouter(repository, ragProperties, List.of(repository)),
                new DeterministicEmbeddingClient(),
                ragProperties,
                new HybridChunkRanker(),
                contentSupport
            )
        );
    }
}
