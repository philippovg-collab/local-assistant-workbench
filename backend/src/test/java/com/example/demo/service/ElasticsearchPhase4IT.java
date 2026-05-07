package com.example.demo.service;

import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.OcrCapabilityProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.ElasticsearchLexicalSearchProvider;
import com.example.demo.infrastructure.material.PostgresMaterialTestRepositoryBundle;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestOverrides.class)
class ElasticsearchPhase4IT extends PostgresIntegrationTestSupport {

    @Container
    @SuppressWarnings("resource")
    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
    )
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void registerElasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.search-sync.enabled", () -> "true");
        registry.add("app.search-sync.index-prefix", () -> "rag-chunks-phase4-it");
        registry.add("app.search-sync.index-version", () -> "v1");
        registry.add("app.rag.shadow-enabled", () -> "true");
        registry.add("app.rag.shadow-sample-percent", () -> "100");
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Autowired
    private MockMvc mockMvc;

    private PostgresMaterialTestRepositoryBundle repository;

    @Autowired
    private MaterialSearchSyncLifecycleService lifecycleService;

    @Autowired
    private ElasticsearchIndexAdminService indexAdminService;

    @Autowired
    private ElasticsearchLexicalSearchProvider elasticsearchLexicalSearchProvider;

    @Autowired
    private LexicalSearchStrategy lexicalSearchStrategy;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetState() throws Exception {
        repository = new PostgresMaterialTestRepositoryBundle(jdbcTemplate, transactionManager);
        jdbcTemplate.execute("TRUNCATE TABLE material_search_sync_queue, material_chunks, materials CASCADE");
        indexAdminService.prepareConfiguredWriteIndex();
        elasticsearchClient.deleteByQuery(delete -> delete
            .index("rag-chunks-phase4-it-write")
            .query(query -> query.matchAll(matchAll -> matchAll))
        );
        refreshIndex();
    }

    @Test
    void registersElasticsearchProviderAlongsidePostgresDefaultProvider() {
        assertEquals(LexicalProviderType.POSTGRES, lexicalSearchStrategy.defaultProviderType());
        assertTrue(lexicalSearchStrategy.find(LexicalProviderType.ELASTICSEARCH).isPresent());
        assertEquals(2, lexicalSearchStrategy.providers().size());
    }

    @Test
    void searchesReadAliasUsingSynonymAndFuzzyQueries() throws Exception {
        StoredMaterialRecord synonymMaterial = seedReadyMaterial(
            "Energy note",
            "Электричество подается стабильно по резервной линии.",
            "energy-lineage",
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord fuzzyMaterial = seedReadyMaterial(
            "Tariff note",
            "Premium tariff includes backup dispatch channel.",
            "tariff-lineage",
            Instant.parse("2026-04-17T10:01:00Z")
        );
        refreshIndex();

        List<MaterialChunkSearchMatch> synonymMatches = elasticsearchLexicalSearchProvider.search("электроэнергия", 5);
        List<MaterialChunkSearchMatch> fuzzyMatches = elasticsearchLexicalSearchProvider.search("premum tarfi", 5);

        assertFalse(synonymMatches.isEmpty());
        assertEquals(synonymMaterial.id(), synonymMatches.getFirst().materialId());
        assertFalse(fuzzyMatches.isEmpty());
        assertEquals(fuzzyMaterial.id(), fuzzyMatches.getFirst().materialId());
    }

    @Test
    void exposesSearchPlaneInHealthContractWithoutChangingRagStatusSemantics() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchStatus").value("UP"))
            .andExpect(jsonPath("$.searchProvider").value("postgres"))
            .andExpect(jsonPath("$.searchSyncBacklog.pendingCount").value(0))
            .andExpect(jsonPath("$.searchSyncBacklog.inProgressCount").value(0))
            .andExpect(jsonPath("$.searchSyncBacklog.failedCount").value(0))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"));
    }

    @Test
    void writesShadowComparisonQualityReportWithNoCriticalRegressionAndAtLeastOneElasticWin() throws Exception {
        StoredMaterialRecord ru = seedReadyMaterial(
            "RU exact",
            "Ремонт подстанции запланирован на май.",
            "ru-lineage",
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord kz = seedReadyMaterial(
            "KZ exact",
            "Желі жүктемесі жоспарлы режимде жұмыс істейді.",
            "kz-lineage",
            Instant.parse("2026-04-17T10:01:00Z")
        );
        StoredMaterialRecord en = seedReadyMaterial(
            "EN exact",
            "Premium tariff includes backup dispatch channel.",
            "en-lineage",
            Instant.parse("2026-04-17T10:02:00Z")
        );
        StoredMaterialRecord synonym = seedReadyMaterial(
            "Synonym case",
            "Электричество подается стабильно по резервной линии.",
            "synonym-lineage",
            Instant.parse("2026-04-17T10:03:00Z")
        );
        StoredMaterialRecord fuzzy = seedReadyMaterial(
            "Typo case",
            "Premium tariff includes backup dispatch channel.",
            "typo-lineage",
            Instant.parse("2026-04-17T10:04:00Z")
        );
        StoredMaterialRecord ocrNoisy = seedReadyMaterial(
            "OCR noisy case",
            "Подстаиция 110 кВ работает стабильно после ремонта.",
            "ocr-lineage",
            Instant.parse("2026-04-17T10:05:00Z")
        );
        refreshIndex();

        List<QualityScenario> scenarios = List.of(
            new QualityScenario("ru-exact", "ремонт подстанции", ru.id()),
            new QualityScenario("kz-exact", "желi жүктемесі", kz.id()),
            new QualityScenario("en-exact", "backup dispatch channel", en.id()),
            new QualityScenario("synonym", "электроэнергия", synonym.id()),
            new QualityScenario("typo", "premum tarfi", fuzzy.id()),
            new QualityScenario("ocr-noisy", "подстанция 110 кВ", ocrNoisy.id())
        );

        List<QualityResult> results = new ArrayList<>();
        boolean hasCriticalRegression = false;
        boolean hasElasticWin = false;

        for (QualityScenario scenario : scenarios) {
            List<MaterialChunkSearchMatch> postgresMatches = repository.lexical().search(scenario.query(), 5);
            List<MaterialChunkSearchMatch> elasticsearchMatches = elasticsearchLexicalSearchProvider.search(scenario.query(), 5);
            boolean postgresHit = hitsExpectedMaterial(postgresMatches, scenario.expectedMaterialId());
            boolean elasticsearchHit = hitsExpectedMaterial(elasticsearchMatches, scenario.expectedMaterialId());
            String verdict;
            if (postgresHit && !elasticsearchHit) {
                verdict = "CRITICAL_REGRESSION";
                hasCriticalRegression = true;
            } else if (!postgresHit && elasticsearchHit) {
                verdict = "ELASTIC_WIN";
                hasElasticWin = true;
            } else if (postgresHit && elasticsearchHit) {
                verdict = "STABLE_BOTH_HIT";
            } else {
                verdict = "MISS_BOTH";
            }
            results.add(new QualityResult(
                scenario.name(),
                scenario.query(),
                verdict,
                postgresMatches,
                elasticsearchMatches
            ));
        }

        writeQualityReport(results);

        assertFalse(hasCriticalRegression, "Elasticsearch lexical path regressed on a case where Postgres still hits.");
        assertTrue(hasElasticWin, "Expected at least one Elasticsearch win on synonym/typo/OCR-noisy cases.");
    }

    private StoredMaterialRecord seedReadyMaterial(
        String title,
        String content,
        String sourceKey,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            "text",
            null,
            "text/plain",
            content,
            content,
            UUID.randomUUID().toString(),
            sourceKey,
            "direct-text",
            false,
            null,
            List.of(new StoredMaterialChunk(0, content, List.of(), 1, "direct-text", false)),
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            null,
            null,
            updatedAt,
            updatedAt
        );

        repository.catalog().save(record, List.of(new StoredMaterialChunk(0, content, List.of(), 1, "direct-text", false)));
        lifecycleService.markIndexingReady(
            record.id(),
            List.of(new StoredEmbeddedMaterialChunk(
                0,
                content,
                1,
                "direct-text",
                false,
                embeddingClient.embed(content)
            )),
            MaterialIndexingStatus.READY,
            null,
            null,
            updatedAt
        );
        return record;
    }

    private boolean hitsExpectedMaterial(List<MaterialChunkSearchMatch> matches, String materialId) {
        return matches.stream().anyMatch(match -> materialId.equals(match.materialId()));
    }

    private void refreshIndex() throws IOException {
        elasticsearchClient.indices().refresh(refresh -> refresh.index("rag-chunks-phase4-it-v1"));
    }

    private void writeQualityReport(List<QualityResult> results) throws Exception {
        Path reportDirectory = Path.of("target", "search-quality");
        Files.createDirectories(reportDirectory);
        Path reportPath = reportDirectory.resolve("phase4-shadow-report.md");

        StringBuilder report = new StringBuilder()
            .append("# Phase 4 Shadow Quality Report\n\n")
            .append("Generated at runtime by `ElasticsearchPhase4IT`.\n\n")
            .append("| Scenario | Verdict | Postgres top hits | Elasticsearch top hits |\n")
            .append("|---|---|---|---|\n");

        for (QualityResult result : results) {
            report.append("| ")
                .append(result.name())
                .append(" | ")
                .append(result.verdict())
                .append(" | ")
                .append(renderHits(result.postgresMatches()))
                .append(" | ")
                .append(renderHits(result.elasticsearchMatches()))
                .append(" |\n");
        }

        Files.writeString(reportPath, report.toString());
    }

    private String renderHits(List<MaterialChunkSearchMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return "none";
        }

        return matches.stream()
            .limit(3)
            .map(match -> match.title() + " [" + match.materialId() + ":" + match.chunkIndex() + "]")
            .reduce((left, right) -> left + "<br/>" + right)
            .orElse("none");
    }

    private record QualityScenario(String name, String query, String expectedMaterialId) {
    }

    private record QualityResult(
        String name,
        String query,
        String verdict,
        List<MaterialChunkSearchMatch> postgresMatches,
        List<MaterialChunkSearchMatch> elasticsearchMatches
    ) {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        OcrCapabilityProvider ocrCapabilityProvider() {
            return () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12);
        }

        @Bean
        @Primary
        LlmClient llmClient() {
            return new LlmClient() {
                @Override
                public List<OllamaModelInfo> listModels() {
                    return List.of(new OllamaModelInfo("qwen2.5:7b"));
                }

                @Override
                public ChatResult chat(ChatRequest request) {
                    return new ChatResult("qwen2.5:7b", "ok", "2026-04-17T10:00:00Z", 1, 1, 2);
                }
            };
        }
    }
}
