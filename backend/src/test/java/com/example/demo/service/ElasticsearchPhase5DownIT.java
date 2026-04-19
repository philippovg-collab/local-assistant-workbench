package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.PostgresMaterialRepository;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
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

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestOverrides.class)
class ElasticsearchPhase5DownIT extends PostgresIntegrationTestSupport {

    @DynamicPropertySource
    static void registerElasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.search-sync.enabled", () -> "true");
        registry.add("app.search-sync.index-prefix", () -> "rag-chunks-phase5-down-it");
        registry.add("app.search-sync.index-version", () -> "v1");
        registry.add("app.rag.lexical-provider", () -> "auto");
        registry.add("spring.elasticsearch.uris", () -> "http://127.0.0.1:9233");
    }

    @Autowired
    private PostgresMaterialRepository repository;

    @Autowired
    private MaterialSearchSyncLifecycleService lifecycleService;

    @Autowired
    private MaterialRetrievalService materialRetrievalService;

    @Autowired
    private ProductionLexicalSearchRouter productionLexicalSearchRouter;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE TABLE material_search_sync_queue, material_chunks, materials CASCADE");
    }

    @Test
    void autoFallsBackToPostgresWhenElasticsearchClusterIsDown() {
        seedReadyMaterial(
            "Pricing FAQ",
            "Premium tariff includes backup dispatch channel.",
            "pricing-lineage",
            Instant.parse("2026-04-17T10:00:00Z")
        );

        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = productionLexicalSearchRouter.currentDecisionWithRefresh();
        MaterialRetrievalResult retrievalResult = materialRetrievalService.retrieveContext("backup dispatch channel");

        assertEquals("DOWN", decision.searchHealth().clusterStatus());
        assertEquals("postgres", decision.effectiveProvider().propertyValue());
        assertEquals("search.cluster_unavailable", decision.fallbackReasonCode());
        assertTrue(decision.fallbackApplied());
        assertFalse(retrievalResult.sources().isEmpty());
    }

    private void seedReadyMaterial(
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

        repository.save(record, List.of(new StoredMaterialChunk(0, content, List.of(), 1, "direct-text", false)));
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
