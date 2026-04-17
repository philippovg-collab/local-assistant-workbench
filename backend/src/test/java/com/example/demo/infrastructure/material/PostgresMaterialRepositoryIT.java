package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresMaterialRepositoryIT extends PostgresIntegrationTestSupport {

    private PostgresMaterialRepository repository;
    private JdbcTemplate jdbcTemplate;
    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        repository = new PostgresMaterialRepository(database.jdbcTemplate(), database.transactionManager());
        jdbcTemplate = database.jdbcTemplate();
    }

    @Test
    void savesFindsAndDeletesMaterialsWithChunks() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Premium support plan costs 12000 tenge.",
            "hash-save-delete",
            MaterialIndexingStatus.PENDING
        );

        repository.save(record, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));

        assertEquals(1, repository.countMaterials());
        assertEquals(0, repository.countReadyMaterials());
        assertEquals(record.id(), repository.findById(record.id()).orElseThrow().id());
        assertEquals(record.id(), repository.findByContentHash(record.contentHash()).orElseThrow().id());
        assertEquals(1, repository.findChunks(record.id()).size());

        repository.delete(record.id());

        assertTrue(repository.findById(record.id()).isEmpty());
        assertEquals(0, repository.countMaterials());
        Integer chunkCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_chunks", Integer.class);
        assertEquals(0, chunkCount);
    }

    @Test
    void deduplicatesWritesByContentHash() {
        StoredMaterialRecord first = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Premium support plan costs 12000 tenge.",
            "hash-dedup",
            MaterialIndexingStatus.PENDING
        );
        StoredMaterialRecord duplicate = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ Copy",
            "Premium support plan costs 12000 tenge.",
            "hash-dedup",
            MaterialIndexingStatus.PENDING
        );

        repository.save(first, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));
        StoredMaterialRecord persisted = repository.save(
            duplicate,
            List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1))
        );

        assertEquals(first.id(), persisted.id());
        assertEquals(1, repository.countMaterials());
    }

    @Test
    void managesDurableIndexingQueueLifecycle() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Queued material",
            "Queue lifecycle proof content.",
            "hash-queue",
            MaterialIndexingStatus.PENDING
        );
        repository.save(record, List.of(rawChunk(0, "Queue lifecycle proof content.", 1)));

        Instant firstClaimAt = Instant.parse("2026-04-17T10:00:00Z");
        assertTrue(repository.hasPendingIndexing(firstClaimAt));

        MaterialIndexingLease firstLease = repository.claimNextIndexing(firstClaimAt).orElseThrow();
        assertEquals(record.id(), firstLease.record().id());
        assertEquals(1, firstLease.attemptNumber());
        assertFalse(repository.hasPendingIndexing(firstClaimAt));

        Instant nextRetryAt = firstClaimAt.plusSeconds(120);
        repository.rescheduleIndexing(
            record.id(),
            "embedding.provider_unavailable",
            "Retry later",
            firstClaimAt.plusSeconds(1),
            nextRetryAt
        );

        assertFalse(repository.hasPendingIndexing(nextRetryAt.minusSeconds(1)));
        assertTrue(repository.hasPendingIndexing(nextRetryAt));

        MaterialIndexingLease secondLease = repository.claimNextIndexing(nextRetryAt).orElseThrow();
        assertEquals(2, secondLease.attemptNumber());

        Instant recoveredAt = nextRetryAt.plusSeconds(60);
        repository.resetExpiredIndexingClaims(nextRetryAt.plusSeconds(30), recoveredAt);

        StoredMaterialRecord recoveredRecord = repository.findById(record.id()).orElseThrow();
        assertEquals(MaterialIndexingStatus.PENDING, recoveredRecord.status());
        assertTrue(repository.hasPendingIndexing(recoveredAt));
    }

    @Test
    void storesEmbeddedChunksAndSupportsSemanticAndLexicalSearch() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Premium support plan costs 12000 tenge.",
            "hash-search",
            MaterialIndexingStatus.PENDING
        );
        repository.save(record, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));

        repository.markIndexingReady(
            record.id(),
            List.of(new StoredEmbeddedMaterialChunk(
                0,
                "Premium support plan costs 12000 tenge.",
                1,
                "direct-text",
                false,
                embeddingClient.embed("premium support plan costs 12000 tenge")
            )),
            MaterialIndexingStatus.PARTIAL_READY,
            "material.partial_ocr",
            "Indexed with partial OCR coverage",
            Instant.parse("2026-04-17T10:05:00Z")
        );

        StoredMaterialRecord reloaded = repository.findById(record.id()).orElseThrow();
        assertEquals(MaterialIndexingStatus.PARTIAL_READY, reloaded.status());
        assertEquals("material.partial_ocr", reloaded.statusReasonCode());
        assertEquals(1, repository.countReadyMaterials());
        assertEquals(1, repository.findChunks(record.id()).size());

        List<MaterialChunkSearchMatch> lexicalMatches = repository.searchLexical("premium support", 5);
        assertFalse(lexicalMatches.isEmpty());
        assertEquals(record.id(), lexicalMatches.getFirst().materialId());
        assertNotNull(lexicalMatches.getFirst().lexicalScore());

        List<MaterialChunkSearchMatch> semanticMatches = repository.searchSemantic(
            embeddingClient.embed("premium support"),
            5
        );
        assertFalse(semanticMatches.isEmpty());
        assertEquals(record.id(), semanticMatches.getFirst().materialId());
        assertNotNull(semanticMatches.getFirst().semanticDistance());
    }

    @Test
    void countsOnlyActiveLineageAndFindsLatestSupersededVersion() {
        StoredMaterialRecord first = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Старая цена 9000 тенге.",
            "hash-lineage-first",
            MaterialIndexingStatus.READY
        );
        StoredMaterialRecord second = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "file",
            "Pricing_FAQ.txt",
            "text/plain",
            "Новая цена 12000 тенге.",
            "Новая цена 12000 тенге.",
            "hash-lineage-second",
            first.sourceKey(),
            "direct-text",
            false,
            1,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            first.createdAt().plusSeconds(60),
            first.updatedAt().plusSeconds(60)
        );

        repository.save(first, List.of(rawChunk(0, "Старая цена 9000 тенге.", 1)));
        repository.save(second, List.of(rawChunk(0, "Новая цена 12000 тенге.", 1)));
        repository.supersedeActiveVersions(
            first.sourceKey(),
            second.id(),
            second.contentHash(),
            "material.superseded_by_new_active_version",
            Instant.parse("2026-04-17T10:10:00Z")
        );

        assertEquals(1, repository.countActiveMaterials());
        assertEquals(first.id(), repository.findLatestBySourceKeyAndVersionState(
            first.sourceKey(),
            MaterialVersionState.SUPERSEDED
        ).orElseThrow().id());
    }

    @Test
    void countReadyMaterialsAndSearchIgnoreSupersededReadyVersionsWhenNoActiveReadyExists() {
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord supersededReady = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "legacybeta support plan costs 9000 tenge.",
            "hash-superseded-ready",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime
        );
        StoredMaterialRecord activePending = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "currentgamma support plan is being reindexed.",
            "hash-active-pending",
            "pricing-lineage",
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(60)
        );

        saveReadyMaterial(supersededReady, "legacybeta support plan costs 9000 tenge.");
        repository.save(activePending, List.of(rawChunk(0, "currentgamma support plan is being reindexed.", 1)));

        assertEquals(2, repository.countMaterials());
        assertEquals(1, repository.countActiveMaterials());
        assertEquals(0, repository.countReadyMaterials());
        assertTrue(repository.searchLexical("legacybeta", 5).isEmpty());
        assertTrue(repository.searchSemantic(embeddingClient.embed("legacybeta"), 5).isEmpty());
    }

    @Test
    void searchFiltersToActiveVersionsEvenWhenSupersededVersionMatchesBetter() {
        Instant baseTime = Instant.parse("2026-04-17T10:20:00Z");
        StoredMaterialRecord supersededReady = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "legacybeta support plan costs 9000 tenge.",
            "hash-superseded-search",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime
        );
        StoredMaterialRecord activeReady = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "currentgamma support plan costs 12000 tenge.",
            "hash-active-search",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(60)
        );

        saveReadyMaterial(supersededReady, "legacybeta support plan costs 9000 tenge.");
        saveReadyMaterial(activeReady, "currentgamma support plan costs 12000 tenge.");

        List<MaterialChunkSearchMatch> lexicalMatches = repository.searchLexical("legacybeta", 5);
        assertTrue(lexicalMatches.isEmpty());

        List<MaterialChunkSearchMatch> semanticMatches = repository.searchSemantic(
            embeddingClient.embed("legacybeta"),
            5
        );
        assertFalse(semanticMatches.isEmpty());
        assertEquals(activeReady.id(), semanticMatches.getFirst().materialId());
    }

    @Test
    void keepsCountsAndSearchConsistentAcrossMultiVersionPromotionChain() {
        String sourceKey = "pricing-lineage";
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord v1 = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "lineage-alpha support plan costs 9000 tenge.",
            "hash-lineage-v1",
            sourceKey,
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime
        );
        StoredMaterialRecord v2 = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "lineage-beta support plan costs 12000 tenge.",
            "hash-lineage-v2",
            sourceKey,
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(60)
        );
        StoredMaterialRecord v3 = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "lineage-gamma support plan costs 15000 tenge.",
            "hash-lineage-v3",
            sourceKey,
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(120)
        );

        saveReadyMaterial(v1, "lineage-alpha support plan costs 9000 tenge.");
        saveReadyMaterial(v2, "lineage-beta support plan costs 12000 tenge.");
        repository.supersedeActiveVersions(
            sourceKey,
            v2.id(),
            v2.contentHash(),
            "material.superseded_by_new_active_version",
            baseTime.plusSeconds(90)
        );

        saveReadyMaterial(v3, "lineage-gamma support plan costs 15000 tenge.");
        repository.supersedeActiveVersions(
            sourceKey,
            v3.id(),
            v3.contentHash(),
            "material.superseded_by_new_active_version",
            baseTime.plusSeconds(150)
        );

        assertEquals(3, repository.countMaterials());
        assertEquals(1, repository.countActiveMaterials());
        assertEquals(1, repository.countReadyMaterials());
        assertEquals(
            v2.id(),
            repository.findLatestBySourceKeyAndVersionState(sourceKey, MaterialVersionState.SUPERSEDED).orElseThrow().id()
        );
        assertTrue(repository.searchLexical("lineage-beta", 5).isEmpty());
        assertEquals(v3.id(), repository.searchSemantic(embeddingClient.embed("lineage-beta"), 5).getFirst().materialId());

        repository.updateVersionState(v3.id(), MaterialVersionState.SUPERSEDED, v2.id(), "material.manual_promotion", baseTime.plusSeconds(180));
        repository.updateVersionState(v2.id(), MaterialVersionState.ACTIVE, null, null, baseTime.plusSeconds(181));

        assertEquals(1, repository.countActiveMaterials());
        assertEquals(1, repository.countReadyMaterials());
        assertTrue(repository.searchLexical("lineage-gamma", 5).isEmpty());
        assertEquals(v2.id(), repository.searchLexical("lineage-beta", 5).getFirst().materialId());
        assertEquals(v2.id(), repository.searchSemantic(embeddingClient.embed("lineage-beta"), 5).getFirst().materialId());
    }

    private StoredMaterialRecord materialRecord(
        String id,
        String title,
        String content,
        String contentHash,
        MaterialIndexingStatus status
    ) {
        Instant timestamp = Instant.parse("2026-04-17T09:55:00Z");
        return materialRecord(
            id,
            title,
            content,
            contentHash,
            title.toLowerCase().replace(' ', '-'),
            status,
            MaterialVersionState.ACTIVE,
            timestamp
        );
    }

    private StoredMaterialRecord materialRecord(
        String id,
        String title,
        String content,
        String contentHash,
        String sourceKey,
        MaterialIndexingStatus status,
        MaterialVersionState versionState,
        Instant timestamp
    ) {
        return new StoredMaterialRecord(
            id,
            title,
            "file",
            title.replace(' ', '_') + ".txt",
            "text/plain",
            content,
            content,
            contentHash,
            sourceKey,
            "direct-text",
            false,
            1,
            List.of(),
            status,
            versionState,
            null,
            null,
            timestamp,
            timestamp
        );
    }

    private StoredMaterialChunk rawChunk(int index, String text, Integer page) {
        return new StoredMaterialChunk(index, text, List.of(), page, "direct-text", false);
    }

    private void saveReadyMaterial(StoredMaterialRecord record, String embeddedText) {
        repository.save(record, List.of(rawChunk(0, embeddedText, 1)));
        repository.markIndexingReady(
            record.id(),
            List.of(new StoredEmbeddedMaterialChunk(
                0,
                embeddedText,
                1,
                "direct-text",
                false,
                embeddingClient.embed(embeddedText)
            )),
            record.status(),
            null,
            null,
            record.updatedAt()
        );
    }
}
