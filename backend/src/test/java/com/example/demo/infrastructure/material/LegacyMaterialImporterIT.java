package com.example.demo.infrastructure.material;

import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.port.LexicalSearchProvider;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.service.HybridChunkRanker;
import com.example.demo.service.LexicalSearchStrategy;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.MaterialContentSupport;
import com.example.demo.service.MaterialIndexingService;
import com.example.demo.service.MaterialIngestionService;
import com.example.demo.service.MaterialQueryService;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.MaterialMetadataResolver;
import com.example.demo.service.MaterialSearchSyncLifecycleService;
import com.example.demo.service.MaterialService;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LegacyMaterialImporterIT extends PostgresIntegrationTestSupport {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway flyway = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();
        flyway.clean();
        flyway.migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void importsLegacyFileStorageIntoPostgres() throws Exception {
        Path storageDir = Files.createTempDirectory("legacy-material-import");
        Files.createDirectories(storageDir.resolve("materials"));
        Files.writeString(
            storageDir.resolve("materials").resolve("legacy-pricing.json"),
            """
                {
                  "id":"c6dd6caa-ed4c-4135-8f03-e67d3fbdc4de",
                  "title":"Pricing note",
                  "sourceType":"file",
                  "originalFileName":"smoke-material.txt",
                  "mediaType":"text/plain",
                  "content":"Тариф Премиум стоит 12000 тенге в месяц и включает приоритетную поддержку.",
                  "extractor":"legacy",
                  "ocrUsed":false,
                  "createdAt":"2026-04-15T16:12:34.078741Z",
                  "updatedAt":"2026-04-15T16:12:34.078742Z"
                }
                """
        );

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        MaterialCatalogRepository catalogRepository = new PostgresMaterialCatalogAdapter(jdbcTemplate, transactionManager);
        MaterialLineageRepository lineageRepository = new PostgresMaterialLineageAdapter(jdbcTemplate, transactionManager);
        MaterialChunkingRepository chunkingRepository = new PostgresMaterialChunkingAdapter(jdbcTemplate, transactionManager);
        MaterialIndexingQueueRepository indexingQueueRepository = new PostgresMaterialIndexingQueueAdapter(
            jdbcTemplate,
            transactionManager
        );
        MaterialSearchSyncQueueRepository searchSyncQueueRepository = new PostgresMaterialSearchSyncQueueAdapter(
            jdbcTemplate,
            transactionManager
        );
        PostgresMaterialRetrievalSearchAdapter retrievalSearchAdapter = new PostgresMaterialRetrievalSearchAdapter(
            jdbcTemplate,
            transactionManager
        );
        SemanticSearchRepository semanticSearchRepository = retrievalSearchAdapter;
        LexicalSearchProvider lexicalSearchProvider = retrievalSearchAdapter;
        MaterialFormatRegistry formatRegistry = new MaterialFormatRegistry();
        MaterialProperties materialProperties = new MaterialProperties();
        OcrProperties ocrProperties = new OcrProperties();
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
        RoutingDocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new PlainTextDocumentExtractionStrategy(formatRegistry),
            new PdfDocumentExtractionStrategy(formatRegistry, ocrProperties, (imagePath, pageNumber) -> "OCR fallback", ocrCapabilityService),
            new TikaDocumentTextExtractor(materialProperties, formatRegistry)
        ));
        MaterialContentSupport contentSupport = new MaterialContentSupport(materialProperties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            catalogRepository,
            lineageRepository,
            chunkingRepository,
            indexingQueueRepository,
            searchSyncQueueRepository
        );
        AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();
        MaterialIndexingService materialIndexingService = new MaterialIndexingService(
            chunkingRepository,
            indexingQueueRepository,
            contentSupport,
            new DeterministicEmbeddingClient(),
            materialProperties,
            lifecycleService,
            Runnable::run
        );
        RagProperties ragProperties = new RagProperties();
        MaterialService materialService = new MaterialService(
            new MaterialQueryService(
                catalogRepository,
                chunkingRepository,
                materialProperties,
                formatRegistry,
                ocrCapabilityService,
                contentSupport,
                lifecycleService,
                materialIndexingService,
                afterCommitExecutor
            ),
            new MaterialIngestionService(
                catalogRepository,
                lineageRepository,
                extractor,
                materialProperties,
                contentSupport,
                new MaterialMetadataResolver(),
                lifecycleService,
                materialIndexingService,
                afterCommitExecutor
            ),
            TestMaterialServices.retrievalService(
                catalogRepository,
                chunkingRepository,
                semanticSearchRepository,
                TestLexicalRoutingSupport.productionRouter(
                    searchSyncQueueRepository,
                    ragProperties,
                    List.of(lexicalSearchProvider)
                ),
                new DeterministicEmbeddingClient(),
                ragProperties,
                new HybridChunkRanker(),
                contentSupport
            )
        );
        LegacyMaterialImporter importer = new LegacyMaterialImporter(
            new FileMaterialRepository(objectMapper, storageDir.toString()),
            materialService
        );

        importer.run(new DefaultApplicationArguments(new String[0]));

        Integer materialCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM materials", Integer.class);
        Integer chunkCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_chunks", Integer.class);
        String contentHash = jdbcTemplate.queryForObject("SELECT content_hash FROM materials LIMIT 1", String.class);

        assertEquals(1, materialCount);
        assertEquals(1, chunkCount);
        assertNotNull(contentHash);
    }
}
