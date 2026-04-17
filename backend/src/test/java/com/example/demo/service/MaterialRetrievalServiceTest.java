package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialRetrievalServiceTest {

    @Test
    void returnsNoSourcesWhenCatalogContainsOnlyHistoricalVersions() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Историческая версия: тариф стоил 9000 тенге.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Какая цена?");

        assertEquals(1, result.materialCount());
        assertEquals(0, result.activeMaterialCount());
        assertEquals(0, result.readyMaterialCount());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void retrievesOnlyActiveLineageEvenWhenSupersededVersionWouldMatchBetter() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        StoredMaterialRecord superseded = saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Старая цена: 9000 тенге.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Новая цена: 12000 тенге.",
            superseded.sourceKey(),
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Какая старая цена?");

        assertEquals(2, result.materialCount());
        assertEquals(1, result.activeMaterialCount());
        assertEquals(1, result.readyMaterialCount());
        assertFalse(result.sources().isEmpty());
        assertTrue(result.sources().stream().noneMatch(source -> source.excerpt().contains("9000")));
        assertTrue(result.sources().stream().allMatch(source -> source.excerpt().contains("12000")));
    }

    @Test
    void treatsPartialReadyActiveVersionsAsReadyForRetrieval() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Частично готовый индекс уже содержит тариф 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PARTIAL_READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Какой тариф уже доступен?");

        assertEquals(1, result.materialCount());
        assertEquals(1, result.activeMaterialCount());
        assertEquals(1, result.readyMaterialCount());
        assertFalse(result.sources().isEmpty());
        assertTrue(result.sources().stream().allMatch(source -> source.excerpt().contains("12000")));
    }

    @Test
    void countsReadyMaterialsUsingOnlyActiveVersions() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Историческая версия: тариф стоил 9000 тенге.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Новая версия ещё индексируется.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Какая цена?");

        assertEquals(2, result.materialCount());
        assertEquals(1, result.activeMaterialCount());
        assertEquals(0, result.readyMaterialCount());
        assertTrue(result.sources().isEmpty());
    }

    private MaterialRetrievalService createService(
        InMemoryMaterialRepository repository,
        DeterministicEmbeddingClient embeddingClient
    ) {
        MaterialProperties properties = new MaterialProperties();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        return new MaterialRetrievalService(
            repository,
            repository,
            embeddingClient,
            new RagProperties(),
            new HybridChunkRanker(),
            contentSupport
        );
    }

    private StoredMaterialRecord saveMaterial(
        InMemoryMaterialRepository repository,
        DeterministicEmbeddingClient embeddingClient,
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        MaterialIndexingStatus status,
        Instant timestamp
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
            List.of(new StoredMaterialChunk(
                0,
                content,
                List.of(),
                null,
                "direct-text",
                false
            )),
            status,
            versionState,
            null,
            null,
            timestamp,
            timestamp
        );

        repository.save(record, record.chunks());

        if (status == MaterialIndexingStatus.READY || status == MaterialIndexingStatus.PARTIAL_READY) {
            repository.markIndexingReady(
                record.id(),
                List.of(new StoredEmbeddedMaterialChunk(
                    0,
                    content,
                    null,
                    "direct-text",
                    false,
                    embeddingClient.embed(content)
                )),
                status,
                null,
                null,
                timestamp
            );
        }

        return record;
    }
}
