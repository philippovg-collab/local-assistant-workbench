package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.service.HybridChunkRanker;
import com.example.demo.service.MaterialContentSupport;
import com.example.demo.service.MaterialIndexingService;
import com.example.demo.service.MaterialIngestionService;
import com.example.demo.service.MaterialQueryService;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.MaterialService;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.PostgresIntegrationTestSupport;
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
        PostgresMaterialRepository postgresRepository = new PostgresMaterialRepository(
            new JdbcTemplate(dataSource),
            new DataSourceTransactionManager(dataSource)
        );
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
        MaterialIndexingService materialIndexingService = new MaterialIndexingService(
            postgresRepository,
            postgresRepository,
            contentSupport,
            new DeterministicEmbeddingClient(),
            materialProperties,
            Runnable::run
        );
        MaterialService materialService = new MaterialService(
            new MaterialQueryService(
                postgresRepository,
                postgresRepository,
                materialProperties,
                formatRegistry,
                ocrCapabilityService,
                contentSupport,
                materialIndexingService
            ),
            new MaterialIngestionService(
                postgresRepository,
                postgresRepository,
                extractor,
                materialProperties,
                contentSupport,
                materialIndexingService
            ),
            new MaterialRetrievalService(
                postgresRepository,
                postgresRepository,
                new DeterministicEmbeddingClient(),
                new RagProperties(),
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
