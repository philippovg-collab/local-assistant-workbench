package com.example.demo.service;

import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.SearchableChunkDocument;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.OcrCapabilityProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.PostgresMaterialTestRepositoryBundle;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestOverrides.class)
class ElasticsearchPhase5IT extends PostgresIntegrationTestSupport {

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
        registry.add("app.search-sync.index-prefix", () -> "rag-chunks-phase5-it");
        registry.add("app.search-sync.index-version", () -> "v1");
        registry.add("app.rag.lexical-provider", () -> "auto");
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    private PostgresMaterialTestRepositoryBundle repository;

    @Autowired
    private MaterialSearchSyncLifecycleService lifecycleService;

    @Autowired
    private MaterialRetrievalService materialRetrievalService;

    @Autowired
    private ProductionLexicalSearchRouter productionLexicalSearchRouter;

    @Autowired
    private ElasticsearchIndexAdminService indexAdminService;

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
            .index("rag-chunks-phase5-it-write")
            .query(query -> query.matchAll(matchAll -> matchAll))
        );
        refreshIndex();
    }

    @Test
    void autoUsesElasticsearchWhenSearchPlaneIsHealthy() throws Exception {
        seedReadyMaterial(
            "Pricing FAQ",
            "Premium tariff includes backup dispatch channel.",
            "pricing-lineage",
            Instant.parse("2026-04-17T10:00:00Z")
        );
        refreshIndex();

        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = productionLexicalSearchRouter.currentDecision();
        MaterialRetrievalResult retrievalResult = materialRetrievalService.retrieveContext("backup dispatch channel");

        assertEquals("auto", decision.configuredMode().propertyValue());
        assertEquals("UP", decision.searchHealth().clusterStatus());
        assertEquals("elasticsearch", decision.effectiveProvider().propertyValue());
        assertFalse(decision.fallbackApplied());
        assertFalse(retrievalResult.sources().isEmpty());
    }

    @Test
    void autoFallsBackToPostgresWhenSearchBacklogIsStale() throws Exception {
        StoredMaterialRecord record = seedReadyMaterial(
            "Pricing FAQ",
            "Premium tariff includes backup dispatch channel.",
            "pricing-lineage",
            Instant.parse("2026-04-17T10:00:00Z")
        );
        refreshIndex();
        jdbcTemplate.update(
            """
                INSERT INTO material_search_sync_queue (
                    material_id,
                    delivery_state,
                    attempt_count,
                    next_attempt_at,
                    claimed_at,
                    last_error_code,
                    last_error_message,
                    requested_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(record.id()),
            "PENDING",
            0,
            null,
            null,
            null,
            null,
            Timestamp.from(Instant.parse("2026-04-17T09:30:00Z")),
            Timestamp.from(Instant.parse("2026-04-17T09:30:00Z")),
            Timestamp.from(Instant.parse("2026-04-17T09:30:00Z"))
        );

        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = productionLexicalSearchRouter.currentDecision();
        MaterialRetrievalResult retrievalResult = materialRetrievalService.retrieveContext("backup dispatch channel");

        assertEquals("DEGRADED", decision.searchHealth().clusterStatus());
        assertEquals("search.sync_backlog_stale", decision.fallbackReasonCode());
        assertEquals("postgres", decision.effectiveProvider().propertyValue());
        assertTrue(decision.fallbackApplied());
        assertFalse(retrievalResult.sources().isEmpty());
    }

    @Test
    void autoTracksDeletePromotionAndManualReactivationThroughElasticsearchLifecycle() throws Exception {
        StoredMaterialRecord original = persistReadyMaterial(
            "Pricing FAQ",
            "Legacy tariff keeps the SMS hotline fallback.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:55:00Z")
        );
        StoredMaterialRecord current = saveNewReadyActiveMaterial(
            "Pricing FAQ",
            "Current tariff adds the backup dispatch channel.",
            "pricing-lineage",
            Instant.parse("2026-04-17T10:00:00Z")
        );
        refreshIndex();

        assertTrue(documentsFor(original.id()).isEmpty());
        assertEquals(1, documentsFor(current.id()).size());

        MaterialRetrievalResult currentResult = materialRetrievalService.retrieveContext("backup dispatch channel");
        assertFalse(currentResult.sources().isEmpty());
        assertTrue(currentResult.sources().stream().allMatch(source -> source.excerpt().contains("backup dispatch channel")));

        lifecycleService.delete(current.id(), Instant.parse("2026-04-17T10:05:00Z"));
        refreshIndex();

        ProductionLexicalSearchRouter.LexicalRoutingDecision afterDelete = productionLexicalSearchRouter.currentDecision();
        MaterialRetrievalResult promotedResult = materialRetrievalService.retrieveContext("sms hotline fallback");

        assertEquals("UP", afterDelete.searchHealth().clusterStatus());
        assertEquals("elasticsearch", afterDelete.effectiveProvider().propertyValue());
        assertFalse(afterDelete.fallbackApplied());
        assertEquals(1, documentsFor(original.id()).size());
        assertTrue(documentsFor(current.id()).isEmpty());
        assertFalse(promotedResult.sources().isEmpty());
        assertTrue(promotedResult.sources().stream().allMatch(source -> source.excerpt().contains("SMS hotline fallback")));

        StoredMaterialRecord replacement = saveNewReadyActiveMaterial(
            "Pricing FAQ",
            "Replacement tariff routes through the satellite hotline.",
            "pricing-lineage",
            Instant.parse("2026-04-17T10:10:00Z")
        );
        refreshIndex();

        assertTrue(documentsFor(original.id()).isEmpty());
        assertEquals(1, documentsFor(replacement.id()).size());

        StoredMaterialRecord reactivated = lifecycleService.reactivateVersion(
            repository.catalog().findById(original.id()).orElseThrow(),
            "material.manual_rollback",
            Instant.parse("2026-04-17T10:15:00Z")
        );
        refreshIndex();

        ProductionLexicalSearchRouter.LexicalRoutingDecision afterReactivate = productionLexicalSearchRouter.currentDecision();
        MaterialRetrievalResult reactivatedResult = materialRetrievalService.retrieveContext("sms hotline fallback");

        assertEquals(MaterialVersionState.ACTIVE, repository.catalog().findById(reactivated.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.catalog().findById(replacement.id()).orElseThrow().versionState());
        assertEquals("UP", afterReactivate.searchHealth().clusterStatus());
        assertEquals("elasticsearch", afterReactivate.effectiveProvider().propertyValue());
        assertFalse(afterReactivate.fallbackApplied());
        assertEquals(1, documentsFor(original.id()).size());
        assertTrue(documentsFor(replacement.id()).isEmpty());
        assertFalse(reactivatedResult.sources().isEmpty());
        assertTrue(reactivatedResult.sources().stream().allMatch(source -> source.excerpt().contains("SMS hotline fallback")));
        assertTrue(reactivatedResult.sources().stream().noneMatch(source -> source.excerpt().contains("satellite hotline")));
    }

    private StoredMaterialRecord seedReadyMaterial(
        String title,
        String content,
        String sourceKey,
        Instant updatedAt
    ) {
        return persistReadyMaterial(title, content, sourceKey, MaterialVersionState.ACTIVE, updatedAt);
    }

    private StoredMaterialRecord saveNewReadyActiveMaterial(
        String title,
        String content,
        String sourceKey,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = materialRecord(title, content, sourceKey, MaterialVersionState.ACTIVE, updatedAt);
        lifecycleService.saveNewActiveMaterial(
            record,
            List.of(new StoredMaterialChunk(0, content, List.of(), 1, "direct-text", false)),
            "material.superseded_by_new_active_version",
            updatedAt
        );
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
        return repository.catalog().findById(record.id()).orElseThrow();
    }

    private StoredMaterialRecord persistReadyMaterial(
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = materialRecord(title, content, sourceKey, versionState, updatedAt);
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
        return repository.catalog().findById(record.id()).orElseThrow();
    }

    private StoredMaterialRecord materialRecord(
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        Instant updatedAt
    ) {
        return new StoredMaterialRecord(
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
            versionState,
            null,
            null,
            updatedAt,
            updatedAt
        );
    }

    private void refreshIndex() throws IOException {
        elasticsearchClient.indices().refresh(refresh -> refresh.index("rag-chunks-phase5-it-v1"));
    }

    private List<SearchableChunkDocument> documentsFor(String materialId) throws IOException {
        var response = elasticsearchClient.search(search -> search
                .index("rag-chunks-phase5-it-write")
                .size(10)
                .query(query -> query.term(term -> term.field("materialId").value(materialId))),
            SearchableChunkDocument.class
        );
        return response.hits().hits().stream()
            .map(hit -> hit.source())
            .filter(document -> document != null)
            .toList();
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
