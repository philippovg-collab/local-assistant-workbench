package com.example.demo.controller;

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
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaterialControllerMetadataFlowTest {

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
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentType").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.knowledgeDocumentClass").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.workspaceKey").value("MANUAL"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.sourceTrust").value("MANUAL"));

        mockMvc.perform(get("/api/materials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title").value("Grid policy"))
            .andExpect(jsonPath("$[0].metadata.documentType").value("POLICY"))
            .andExpect(jsonPath("$[0].metadata.provenance.fieldOrigins.documentType").value("MANUAL"));
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
            .andExpect(jsonPath("$.metadata.workspaceKey").value("north-upgrade"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.documentType").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.knowledgeDocumentClass").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.workspaceKey").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.author").value("INFERRED"))
            .andExpect(jsonPath("$.metadata.provenance.fieldOrigins.sourceTrust").value("DEFAULT"))
            .andExpect(jsonPath("$.metadata.provenance.fieldConfidence.documentType").exists())
            .andExpect(jsonPath("$.metadata.provenance.fieldConfidence.author").exists());
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

    private MaterialService createService() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialProperties properties = new MaterialProperties();
        MaterialFormatRegistry formatRegistry = new MaterialFormatRegistry();
        OcrProperties ocrProperties = new OcrProperties();
        OcrClient ocrClient = (imagePath, pageNumber) -> "OCR fallback text for page " + pageNumber;
        OcrCapabilityService ocrCapabilityService = new OcrCapabilityService(
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
                afterCommitExecutor
            ),
            new MaterialIngestionService(
                repository,
                repository,
                extractor,
                properties,
                contentSupport,
                new MaterialMetadataResolver(),
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
