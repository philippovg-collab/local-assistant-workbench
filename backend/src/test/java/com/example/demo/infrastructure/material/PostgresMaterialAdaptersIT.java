package com.example.demo.infrastructure.material;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialIndexingLease;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.service.material.MaterialSearchSyncQueueEntry;
import com.example.demo.service.material.QualityLayerCoverageSnapshot;
import com.example.demo.service.material.SearchSyncDeliveryState;
import com.example.demo.service.material.SearchSyncOperationType;
import com.example.demo.service.material.SearchableMaterialSnapshot;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.MetadataValueOrigin;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.service.MaterialContentSupport;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresMaterialAdaptersIT extends PostgresIntegrationTestSupport {

    private PostgresMaterialTestRepositoryBundle repository;
    private JdbcTemplate jdbcTemplate;
    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
    private final MaterialContentSupport contentSupport = new MaterialContentSupport(new MaterialProperties());

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        repository = new PostgresMaterialTestRepositoryBundle(database.jdbcTemplate(), database.transactionManager());
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

        repository.catalog().save(record, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));

        assertEquals(1, repository.catalog().countMaterials());
        assertEquals(0, repository.catalog().countReadyMaterials());
        assertEquals(record.id(), repository.catalog().findById(record.id()).orElseThrow().id());
        assertEquals(
            record.id(),
            repository.catalog().findBySourceKeyAndContentHash(record.sourceKey(), record.contentHash()).orElseThrow().id()
        );
        assertEquals(1, repository.chunking().findChunks(record.id()).size());

        repository.catalog().delete(record.id());

        assertTrue(repository.catalog().findById(record.id()).isEmpty());
        assertEquals(0, repository.catalog().countMaterials());
        Integer chunkCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_chunks", Integer.class);
        assertEquals(0, chunkCount);
    }

    @Test
    void allowsIdenticalContentAcrossDifferentLineages() {
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
            "pricing-faq-copy",
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T09:56:00Z")
        );

        repository.catalog().save(first, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));
        StoredMaterialRecord persisted = repository.catalog().save(
            duplicate,
            List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1))
        );

        assertEquals(duplicate.id(), persisted.id());
        assertEquals(2, repository.catalog().countMaterials());
        assertTrue(repository.catalog().findById(first.id()).isPresent());
        assertTrue(repository.catalog().findById(duplicate.id()).isPresent());
    }

    @Test
    void allowsIdenticalContentAcrossHistoricalLineageVersions() {
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
            first.sourceKey(),
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.SUPERSEDED,
            first.createdAt().plusSeconds(60)
        );

        repository.catalog().save(first, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));
        StoredMaterialRecord persisted = repository.catalog().save(
            duplicate,
            List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1))
        );

        assertEquals(duplicate.id(), persisted.id());
        assertEquals(2, repository.catalog().countMaterials());
    }

    @Test
    void resolvesCanonicalTextIdentityToSingleSourceKey() {
        MaterialLineageIdentity firstIdentity = contentSupport.buildLineageIdentity(
            "text",
            "Pricing FAQ",
            null,
            "Первая редакция тарифа 9000 тенге."
        );
        MaterialLineageIdentity secondIdentity = contentSupport.buildLineageIdentity(
            "text",
            "Pricing FAQ",
            null,
            "Вторая редакция тарифа 12000 тенге."
        );

        String firstSourceKey = repository.lineage().resolveSourceKey(firstIdentity);
        String secondSourceKey = repository.lineage().resolveSourceKey(secondIdentity);

        assertEquals(firstSourceKey, secondSourceKey);
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_lineage_identities", Integer.class));
    }

    @Test
    void resolvesCanonicalFileStemAndAnchorIdentityWithoutFilenameFallbacks() {
        MaterialLineageIdentity firstIdentity = contentSupport.buildLineageIdentity(
            "file",
            null,
            "brief.txt",
            "План ремонта северной подстанции май июнь июль август сентябрь октябрь ноябрь декабрь версия один подробности."
        );
        MaterialLineageIdentity sameLineageIdentity = contentSupport.buildLineageIdentity(
            "file",
            null,
            "brief.txt",
            "План ремонта северной подстанции май июнь июль август сентябрь октябрь ноябрь декабрь версия два уточнения."
        );
        MaterialLineageIdentity differentIdentity = contentSupport.buildLineageIdentity(
            "file",
            null,
            "brief.txt",
            "Регламент закупки трансформаторов и кабеля на четвертый квартал."
        );

        String firstSourceKey = repository.lineage().resolveSourceKey(firstIdentity);
        String sameLineageSourceKey = repository.lineage().resolveSourceKey(sameLineageIdentity);
        String differentSourceKey = repository.lineage().resolveSourceKey(differentIdentity);

        assertEquals(firstSourceKey, sameLineageSourceKey);
        assertNotEquals(firstSourceKey, differentSourceKey);
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_lineage_identities", Integer.class));
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
        repository.catalog().save(record, List.of(rawChunk(0, "Queue lifecycle proof content.", 1)));

        Instant firstClaimAt = Instant.parse("2026-04-17T10:00:00Z");
        assertTrue(repository.indexingQueue().hasPendingIndexing(firstClaimAt));

        MaterialIndexingLease firstLease = repository.indexingQueue().claimNextIndexing(firstClaimAt).orElseThrow();
        assertEquals(record.id(), firstLease.record().id());
        assertEquals(1, firstLease.attemptNumber());
        assertFalse(repository.indexingQueue().hasPendingIndexing(firstClaimAt));

        Instant nextRetryAt = firstClaimAt.plusSeconds(120);
        repository.indexingQueue().rescheduleIndexing(
            record.id(),
            "embedding.provider_unavailable",
            "Retry later",
            firstClaimAt.plusSeconds(1),
            nextRetryAt
        );

        assertFalse(repository.indexingQueue().hasPendingIndexing(nextRetryAt.minusSeconds(1)));
        assertTrue(repository.indexingQueue().hasPendingIndexing(nextRetryAt));

        MaterialIndexingLease secondLease = repository.indexingQueue().claimNextIndexing(nextRetryAt).orElseThrow();
        assertEquals(2, secondLease.attemptNumber());

        Instant recoveredAt = nextRetryAt.plusSeconds(60);
        repository.indexingQueue().resetExpiredIndexingClaims(nextRetryAt.plusSeconds(30), recoveredAt);

        StoredMaterialRecord recoveredRecord = repository.catalog().findById(record.id()).orElseThrow();
        assertEquals(MaterialIndexingStatus.PENDING, recoveredRecord.status());
        assertTrue(repository.indexingQueue().hasPendingIndexing(recoveredAt));
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
        repository.catalog().save(record, List.of(rawChunk(0, "Premium support plan costs 12000 tenge.", 1)));

        repository.indexingQueue().markIndexingReady(
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

        StoredMaterialRecord reloaded = repository.catalog().findById(record.id()).orElseThrow();
        assertEquals(MaterialIndexingStatus.PARTIAL_READY, reloaded.status());
        assertEquals("material.partial_ocr", reloaded.statusReasonCode());
        assertEquals(1, repository.catalog().countReadyMaterials());
        assertEquals(1, repository.chunking().findChunks(record.id()).size());

        List<MaterialChunkSearchMatch> lexicalMatches = repository.lexical().search("premium support", 5);
        assertFalse(lexicalMatches.isEmpty());
        assertEquals(record.id(), lexicalMatches.getFirst().materialId());
        assertNotNull(lexicalMatches.getFirst().lexicalScore());

        List<MaterialChunkSearchMatch> semanticMatches = repository.semantic().searchSemantic(
            embeddingClient.embed("premium support"),
            5
        );
        assertFalse(semanticMatches.isEmpty());
        assertEquals(record.id(), semanticMatches.getFirst().materialId());
        assertNotNull(semanticMatches.getFirst().semanticDistance());
    }

    @Test
    void defaultRetrievalReadyPredicateExcludesNonActiveStatusInvalidPeriodAndSupersededLineage() {
        LocalDate today = LocalDate.now();
        StoredMaterialRecord eligible = materialRecord(
            UUID.randomUUID().toString(),
            "Eligible policy",
            "phase seven eligible retrieval token.",
            "hash-phase-seven-eligible",
            "phase-seven-eligible",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.ACTIVE, today.minusDays(1), today.plusDays(1)));
        StoredMaterialRecord draft = materialRecord(
            UUID.randomUUID().toString(),
            "Draft policy",
            "phase seven draft retrieval token.",
            "hash-phase-seven-draft",
            "phase-seven-draft",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:01:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.DRAFT, today.minusDays(1), today.plusDays(1)));
        StoredMaterialRecord archived = materialRecord(
            UUID.randomUUID().toString(),
            "Archived policy",
            "phase seven archived retrieval token.",
            "hash-phase-seven-archived",
            "phase-seven-archived",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:02:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.ARCHIVED, today.minusDays(1), today.plusDays(1)));
        StoredMaterialRecord revoked = materialRecord(
            UUID.randomUUID().toString(),
            "Revoked policy",
            "phase seven revoked retrieval token.",
            "hash-phase-seven-revoked",
            "phase-seven-revoked",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:03:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.REVOKED, today.minusDays(1), today.plusDays(1)));
        StoredMaterialRecord expired = materialRecord(
            UUID.randomUUID().toString(),
            "Expired policy",
            "phase seven expired retrieval token.",
            "hash-phase-seven-expired",
            "phase-seven-expired",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:04:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.ACTIVE, today.minusDays(10), today.minusDays(1)));
        StoredMaterialRecord future = materialRecord(
            UUID.randomUUID().toString(),
            "Future policy",
            "phase seven future retrieval token.",
            "hash-phase-seven-future",
            "phase-seven-future",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:05:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.ACTIVE, today.plusDays(1), today.plusDays(10)));
        StoredMaterialRecord superseded = materialRecord(
            UUID.randomUUID().toString(),
            "Superseded policy",
            "phase seven superseded retrieval token.",
            "hash-phase-seven-superseded",
            "phase-seven-superseded",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T10:06:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.ACTIVE, today.minusDays(1), today.plusDays(1)));

        List.of(eligible, draft, archived, revoked, expired, future, superseded)
            .forEach(record -> saveReadyMaterial(record, record.content()));

        assertEquals(6, repository.catalog().countActiveMaterials());
        assertEquals(1, repository.catalog().countReadyMaterials());

        List<String> lexicalIds = repository.lexical().search("phase seven retrieval token", 20)
            .stream()
            .map(MaterialChunkSearchMatch::materialId)
            .toList();
        List<String> semanticIds = repository.semantic().searchSemantic(embeddingClient.embed("phase seven retrieval token"), 20)
            .stream()
            .map(MaterialChunkSearchMatch::materialId)
            .toList();
        MaterialRetrievalScopeSnapshot scope = repository.catalog().describeRetrievalScope(
            KnowledgeScope.empty(),
            RetrievalFilters.empty(),
            null,
            null
        );

        assertEquals(List.of(eligible.id()), lexicalIds);
        assertEquals(List.of(eligible.id()), semanticIds);
        assertEquals(1, scope.scopedReadyMaterialCount());
        assertEquals(List.of(eligible.id()), repository.searchableSnapshot().findAllSearchableMaterialIds());
    }

    @Test
    void qualityLayerCoverageUsesCanonicalMetadataFields() {
        StoredMaterialRecord covered = materialRecord(
            UUID.randomUUID().toString(),
            "Covered policy",
            "Covered policy content.",
            "hash-quality-covered",
            MaterialIndexingStatus.READY
        ).withMetadata(retrievalMetadata(DocumentStatus.ACTIVE, null, null));
        StoredMaterialRecord uncovered = materialRecord(
            UUID.randomUUID().toString(),
            "Uncovered material",
            "Uncovered material content.",
            "hash-quality-uncovered",
            MaterialIndexingStatus.READY
        );
        StoredMaterialRecord historicalCovered = materialRecord(
            UUID.randomUUID().toString(),
            "Historical covered policy",
            "Historical policy content.",
            "hash-quality-historical",
            "hash-quality-historical-source",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T10:00:00Z")
        ).withMetadata(retrievalMetadata(DocumentStatus.ACTIVE, null, null));

        repository.catalog().save(covered, List.of(rawChunk(0, covered.content(), 1)));
        repository.catalog().save(uncovered, List.of(rawChunk(0, uncovered.content(), 1)));
        repository.catalog().save(historicalCovered, List.of(rawChunk(0, historicalCovered.content(), 1)));

        QualityLayerCoverageSnapshot snapshot = repository.qualityMetrics().qualityLayerCoverageSnapshot();

        assertEquals(2, snapshot.activeTotal());
        assertEquals(1, snapshot.activeWithEffectiveMetadata());
        assertEquals(2, snapshot.workspaceCovered());
        assertEquals(1, snapshot.documentTypeCovered());
        assertEquals(2, snapshot.documentStatusCovered());
    }

    @Test
    void savesAndLoadsMaterialMetadata() {
        seedWorkspace("north-upgrade", "North Upgrade");
        seedProject("line-a", "north-upgrade", "Line A");

        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.CONTRACT,
            "north-upgrade",
            DocumentStatus.DRAFT,
            "line-a",
            "KZ-2026-0415-ENERGY",
            MaterialLanguageCode.RU,
            List.of("contract", "energy"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-12-31"),
            null,
            LocalDate.parse("2026-04-17"),
            "Legal lead",
            "Legal",
            "v2.1",
            null,
            null,
            null,
            "Line A Display",
            "KazEnergy Service",
            "APPROVED"
        )).withTagLayers(List.of("contract", "energy"), List.of("auto-grid", "energy"));
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            "Contract register",
            "file",
            "contract.txt",
            "text/plain",
            "Договор KZ-2026-0415-ENERGY заключён с KazEnergy Service.",
            "Договор KZ-2026-0415-ENERGY заключён с KazEnergy Service.",
            "hash-metadata",
            "contract-lineage",
            "direct-text",
            false,
            1,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z"),
            0,
            null,
            null,
            null,
            metadata
        );

        repository.catalog().save(record, List.of(rawChunk(0, record.content(), 1)));

        StoredMaterialRecord reloaded = repository.catalog().findById(record.id()).orElseThrow();
        assertEquals(DocumentType.CONTRACT, reloaded.metadata().documentType());
        assertEquals("KZ-2026-0415-ENERGY", reloaded.metadata().documentNumber());
        assertEquals("north-upgrade", reloaded.metadata().workspaceKey());
        assertEquals("line-a", reloaded.metadata().projectKey());
        assertEquals("Line A Display", reloaded.metadata().project());
        assertEquals(DocumentStatus.DRAFT, reloaded.metadata().documentStatus());
        assertEquals("APPROVED", reloaded.metadata().businessStatus());
        assertEquals(MaterialLanguageCode.RU, reloaded.metadata().languageCode());
        assertEquals("ru", reloaded.metadata().language());
        assertEquals(List.of("contract", "energy"), reloaded.metadata().manualTags());
        assertEquals(List.of("auto-grid"), reloaded.metadata().autoTags());
        assertEquals(List.of("contract", "energy", "auto-grid"), reloaded.metadata().effectiveTags());
        assertEquals(List.of("contract", "energy", "auto-grid"), reloaded.metadata().tags());
        assertEquals(SourceTrustLevel.UNKNOWN, reloaded.metadata().sourceTrust());
        assertEquals(MetadataValueOrigin.MANUAL, reloaded.metadata().provenance().fieldOrigins().get("documentType"));
        assertTrue(reloaded.metadata().provenance().fieldConfidence().isEmpty());
        assertEquals(
            "line-a",
            jdbcTemplate.queryForObject("SELECT project_key FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );
        assertEquals(
            "Line A Display",
            jdbcTemplate.queryForObject("SELECT project_name FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );
        assertEquals(
            "DRAFT",
            jdbcTemplate.queryForObject("SELECT document_status FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );
        assertEquals(
            "APPROVED",
            jdbcTemplate.queryForObject("SELECT business_status FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );
        assertEquals(
            "RU",
            jdbcTemplate.queryForObject("SELECT language_code FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );
        assertEquals(
            List.of("MANUAL", "MANUAL", "AUTO"),
            jdbcTemplate.queryForList(
                "SELECT tag_source FROM material_tags WHERE material_id = ? ORDER BY tag_order",
                String.class,
                UUID.fromString(record.id())
            )
        );
        assertEquals(
            1,
            repository.catalog().describeRetrievalScope(
                KnowledgeScope.empty(),
                new RetrievalFilters(null, null, null, null, null, null, null, null, List.of("auto-grid"), null),
                null,
                null
            ).scopedMaterialCount()
        );
    }

    @Test
    void legacyTagRowsDefaultToManualSource() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Legacy tags",
            "Legacy tag content.",
            "hash-legacy-tags",
            MaterialIndexingStatus.READY
        );

        repository.catalog().save(record, List.of(rawChunk(0, record.content(), 1)));
        jdbcTemplate.update(
            "INSERT INTO material_tags (material_id, tag_order, tag_value) VALUES (?, ?, ?)",
            UUID.fromString(record.id()),
            0,
            "legacy"
        );

        assertEquals(
            "MANUAL",
            jdbcTemplate.queryForObject(
                "SELECT tag_source FROM material_tags WHERE material_id = ? AND tag_value = ?",
                String.class,
                UUID.fromString(record.id()),
                "legacy"
            )
        );
    }

    @Test
    void persistsKnowledgeScopeColumnsAndFiltersRetrievalScopeFromPostgres() {
        seedWorkspace("north-upgrade", "North Upgrade");

        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.POLICY,
            KnowledgeDocumentClass.REGULATIONS,
            LocalDate.parse("2026-04-17"),
            "POL-2026-17",
            "Ops lead",
            "Grid operations",
            "v1",
            "ru",
            List.of("policy"),
            SourceTrustLevel.HIGH,
            "North Upgrade",
            "north-upgrade",
            "Internal",
            "ACTIVE",
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-12-31")
        ));
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            "Dispatch policy",
            "file",
            "dispatch-policy.txt",
            "text/plain",
            "Регламент диспетчеризации для north upgrade.",
            "Регламент диспетчеризации для north upgrade.",
            "hash-scope-columns",
            "scope-columns-lineage",
            "direct-text",
            false,
            1,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z"),
            0,
            null,
            null,
            null,
            metadata
        );

        saveReadyMaterial(record, record.content());

        assertEquals(
            "REGULATIONS",
            jdbcTemplate.queryForObject("SELECT knowledge_document_class FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );
        assertEquals(
            "north-upgrade",
            jdbcTemplate.queryForObject("SELECT workspace_key FROM materials WHERE id = ?", String.class, UUID.fromString(record.id()))
        );

        MaterialRetrievalScopeSnapshot matchingScope = repository.catalog().describeRetrievalScope(
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.REGULATIONS), List.of(), "north-upgrade", false),
            RetrievalFilters.empty(),
            null,
            null
        );
        MaterialRetrievalScopeSnapshot wrongClassScope = repository.catalog().describeRetrievalScope(
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), "north-upgrade", false),
            RetrievalFilters.empty(),
            null,
            null
        );
        MaterialRetrievalScopeSnapshot wrongWorkspaceScope = repository.catalog().describeRetrievalScope(
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.REGULATIONS), List.of(), "south-upgrade", false),
            RetrievalFilters.empty(),
            null,
            null
        );

        assertEquals(1, matchingScope.scopedReadyMaterialCount());
        assertEquals(0, wrongClassScope.scopedReadyMaterialCount());
        assertEquals(0, wrongWorkspaceScope.scopedReadyMaterialCount());
    }

    @Test
    void preservesStructuredChunkMetadataAcrossRawAndEmbeddedRoundTrips() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Structured guide",
            "2. Dispatch approval\nRegional dispatcher approves the outage window.",
            "hash-structured-roundtrip",
            MaterialIndexingStatus.PENDING
        );
        StoredMaterialChunk rawChunk = new StoredMaterialChunk(
            0,
            "Dispatch approval\nRegional dispatcher approves the outage window.",
            List.of("dispatch", "approval"),
            2,
            "tika",
            false,
            DocumentBlockType.NARRATIVE,
            List.of("dispatch-approval"),
            List.of("2. Dispatch approval"),
            null,
            null,
            DocumentBlockConfidence.HIGH
        );

        repository.catalog().save(record, List.of(rawChunk));

        StoredMaterialChunk reloadedRawChunk = repository.chunking().findChunks(record.id()).getFirst();
        assertEquals(DocumentBlockType.NARRATIVE, reloadedRawChunk.chunkType());
        assertEquals(List.of("dispatch-approval"), reloadedRawChunk.sectionPath());
        assertEquals(List.of("2. Dispatch approval"), reloadedRawChunk.headingTrail());
        assertEquals(DocumentBlockConfidence.HIGH, reloadedRawChunk.parserConfidence());

        repository.indexingQueue().markIndexingReady(
            record.id(),
            List.of(new StoredEmbeddedMaterialChunk(
                0,
                rawChunk.text(),
                rawChunk.page(),
                rawChunk.extractor(),
                false,
                embeddingClient.embed(rawChunk.text()),
                rawChunk.chunkType(),
                rawChunk.sectionPath(),
                rawChunk.headingTrail(),
                rawChunk.tableId(),
                rawChunk.slideId(),
                rawChunk.parserConfidence()
            )),
            MaterialIndexingStatus.READY,
            null,
            null,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        SearchableMaterialSnapshot snapshot = repository.searchableSnapshot().resolveSearchableSnapshot(record.id());
        assertEquals(1, snapshot.chunks().size());
        assertEquals(DocumentBlockType.NARRATIVE, snapshot.chunks().getFirst().chunkType());
        assertEquals(List.of("dispatch-approval"), snapshot.chunks().getFirst().sectionPath());
        assertEquals(List.of("2. Dispatch approval"), snapshot.chunks().getFirst().headingTrail());
        assertEquals(DocumentBlockConfidence.HIGH, snapshot.chunks().getFirst().parserConfidence());
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
            MaterialVersionState.SUPERSEDED,
            null,
            null,
            first.createdAt().plusSeconds(60),
            first.updatedAt().plusSeconds(60)
        );

        repository.catalog().save(first, List.of(rawChunk(0, "Старая цена 9000 тенге.", 1)));
        repository.catalog().save(second, List.of(rawChunk(0, "Новая цена 12000 тенге.", 1)));

        assertEquals(1, repository.catalog().countActiveMaterials());
        assertEquals(second.id(), repository.catalog().findLatestBySourceKeyAndVersionState(
            first.sourceKey(),
            MaterialVersionState.SUPERSEDED
        ).orElseThrow().id());
    }

    @Test
    void enforcesSingleActiveVersionPerLineageAtDatabaseLevel() {
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord first = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Первая активная версия",
            "hash-active-constraint-first",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime
        );
        StoredMaterialRecord second = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Вторая активная версия",
            "hash-active-constraint-second",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(60)
        );

        repository.catalog().save(first, List.of(rawChunk(0, "Первая активная версия", 1)));

        assertThrows(
            DataAccessException.class,
            () -> jdbcTemplate.update(
                """
                    INSERT INTO materials (
                        id,
                        title,
                        source_type,
                        original_file_name,
                        media_type,
                        content,
                        normalized_content,
                        content_hash,
                        source_key,
                        extractor,
                        ocr_used,
                        page_count,
                        document_type,
                        source_trust,
                        metadata_jsonb,
                        lineage_version,
                        chunk_profile,
                        indexing_status,
                        version_state,
                        indexing_attempts,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                    """,
                UUID.fromString(second.id()),
                second.title(),
                second.sourceType(),
                second.originalFileName(),
                second.mediaType(),
                second.content(),
                second.normalizedContent(),
                second.contentHash(),
                second.sourceKey(),
                second.extractor(),
                second.ocrUsed(),
                second.pageCount(),
                second.metadata().documentType().name(),
                second.metadata().sourceTrust().name(),
                "{\"fieldOrigins\":{\"documentType\":\"DEFAULT\",\"sourceTrust\":\"DEFAULT\"},\"fieldConfidence\":{}}",
                2,
                ChunkProfile.FIXED_V1.propertyValue(),
                second.status().name(),
                second.versionState().name(),
                second.indexingAttempts(),
                java.sql.Timestamp.from(second.createdAt()),
                java.sql.Timestamp.from(second.updatedAt())
            )
        );
    }

    @Test
    void findsLatestSupersededVersionByLineageVersionInsteadOfUpdatedAt() {
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord olderSuperseded = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Первая версия",
            "hash-superseded-order-first",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime
        );
        StoredMaterialRecord newerSuperseded = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Вторая версия",
            "hash-superseded-order-second",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime.plusSeconds(60)
        );
        StoredMaterialRecord active = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Текущая версия",
            "hash-superseded-order-active",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(120)
        );

        repository.catalog().save(olderSuperseded, List.of(rawChunk(0, "Первая версия", 1)));
        repository.catalog().save(newerSuperseded, List.of(rawChunk(0, "Вторая версия", 1)));
        repository.catalog().save(active, List.of(rawChunk(0, "Текущая версия", 1)));

        jdbcTemplate.update(
            "UPDATE materials SET updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(baseTime.plusSeconds(500)),
            UUID.fromString(olderSuperseded.id())
        );

        assertEquals(
            newerSuperseded.id(),
            repository.catalog().findLatestBySourceKeyAndVersionState("pricing-lineage", MaterialVersionState.SUPERSEDED)
                .orElseThrow()
                .id()
        );
    }

    @Test
    void supersedeActiveVersionsReturnsActuallyAffectedRecords() {
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord first = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Первая версия",
            "hash-lineage-return-first",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime
        );
        StoredMaterialRecord second = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Вторая версия",
            "hash-lineage-return-second",
            "pricing-lineage",
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.SUPERSEDED,
            baseTime.plusSeconds(30)
        );
        StoredMaterialRecord excluded = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Третья версия",
            "hash-lineage-return-third",
            "pricing-lineage",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime.plusSeconds(60)
        );

        repository.catalog().save(first, List.of(rawChunk(0, "Первая версия", 1)));
        repository.catalog().save(second, List.of(rawChunk(0, "Вторая версия", 1)));
        repository.catalog().save(excluded, List.of(rawChunk(0, "Третья версия", 1)));

        List<StoredMaterialRecord> affectedRecords = repository.catalog().supersedeActiveVersions(
            "pricing-lineage",
            excluded.id(),
            excluded.id(),
            "material.superseded_by_new_active_version",
            baseTime.plusSeconds(90)
        );

        assertEquals(List.of(first.id()), affectedRecords.stream().map(StoredMaterialRecord::id).toList());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.catalog().findById(first.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.catalog().findById(second.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.catalog().findById(excluded.id()).orElseThrow().versionState());
    }

    @Test
    void findsActivePagesAfterCursorInStableCreatedAtIdOrder() {
        Instant sharedCreatedAt = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord first = materialRecord(
            "00000000-0000-0000-0000-000000000001",
            "Pricing FAQ",
            "Первая версия",
            "hash-page-first",
            "page-lineage-first",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            sharedCreatedAt
        );
        StoredMaterialRecord second = materialRecord(
            "00000000-0000-0000-0000-000000000002",
            "Pricing FAQ",
            "Вторая версия",
            "hash-page-second",
            "page-lineage-second",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            sharedCreatedAt
        );
        StoredMaterialRecord third = materialRecord(
            "00000000-0000-0000-0000-000000000003",
            "Pricing FAQ",
            "Третья версия",
            "hash-page-third",
            "page-lineage-third",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            sharedCreatedAt.plusSeconds(60)
        );
        StoredMaterialRecord superseded = materialRecord(
            "00000000-0000-0000-0000-000000000004",
            "Pricing FAQ",
            "Историческая версия",
            "hash-page-superseded",
            "page-lineage-third",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            sharedCreatedAt.plusSeconds(120)
        );

        repository.catalog().save(first, List.of(rawChunk(0, "Первая версия", 1)));
        repository.catalog().save(second, List.of(rawChunk(0, "Вторая версия", 1)));
        repository.catalog().save(third, List.of(rawChunk(0, "Третья версия", 1)));
        repository.catalog().save(superseded, List.of(rawChunk(0, "Историческая версия", 1)));

        List<StoredMaterialRecord> firstPage = repository.catalog().findActivePageAfter(null, null, 2);
        List<StoredMaterialRecord> secondPage = repository.catalog().findActivePageAfter(
            firstPage.getLast().createdAt(),
            firstPage.getLast().id(),
            2
        );

        assertEquals(List.of(first.id(), second.id()), firstPage.stream().map(StoredMaterialRecord::id).toList());
        assertEquals(List.of(third.id()), secondPage.stream().map(StoredMaterialRecord::id).toList());
    }

    @Test
    void enqueuesAndReadsSearchSyncQueueEntries() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Premium support plan costs 12000 tenge.",
            "hash-sync-event",
            MaterialIndexingStatus.READY
        );
        saveReadyMaterial(record, "Premium support plan costs 12000 tenge.");
        Instant createdAt = Instant.parse("2026-04-17T10:00:00Z");

        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), createdAt);

        List<MaterialSearchSyncQueueEntry> entries = repository.searchSyncQueue().findAllSearchSyncEntries();

        assertEquals(1, entries.size());
        assertEquals(record.id(), entries.getFirst().materialId());
        assertEquals(SearchSyncDeliveryState.PENDING, entries.getFirst().deliveryState());
        assertEquals(createdAt, entries.getFirst().requestedAt());
    }

    @Test
    void managesSearchSyncQueueLifecycleAndSnapshot() {
        StoredMaterialRecord firstRecord = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Premium support plan costs 12000 tenge.",
            "hash-sync-queue-first",
            MaterialIndexingStatus.READY
        );
        StoredMaterialRecord secondRecord = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ Copy",
            "Premium support plan costs 12000 tenge copy.",
            "hash-sync-queue-second",
            MaterialIndexingStatus.READY
        );
        saveReadyMaterial(firstRecord, "Premium support plan costs 12000 tenge.");
        saveReadyMaterial(secondRecord, "Premium support plan costs 12000 tenge copy.");

        Instant createdAt = Instant.parse("2026-04-17T10:00:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(firstRecord.id(), secondRecord.id()), createdAt);

        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot initialSnapshot = repository.searchSyncQueue().getSearchSyncQueueSnapshot();
        assertEquals(2, initialSnapshot.pendingCount());
        assertEquals(0, initialSnapshot.inProgressCount());
        assertEquals(0, initialSnapshot.failedCount());

        Instant firstClaimAt = createdAt.plusSeconds(10);
        List<MaterialSearchSyncQueueEntry> claimed = repository.searchSyncQueue().claimNextSearchSyncBatch(firstClaimAt, 2);
        assertEquals(sortedIds(firstRecord.id(), secondRecord.id()), sortedEntryMaterialIds(claimed));
        assertEquals(List.of(1, 1), claimed.stream().map(MaterialSearchSyncQueueEntry::attemptCount).sorted().toList());

        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot claimedSnapshot = repository.searchSyncQueue().getSearchSyncQueueSnapshot();
        assertEquals(0, claimedSnapshot.pendingCount());
        assertEquals(2, claimedSnapshot.inProgressCount());
        assertEquals(0, claimedSnapshot.failedCount());

        Instant recoveredAt = firstClaimAt.plusSeconds(30);
        repository.searchSyncQueue().resetExpiredSearchSyncClaims(firstClaimAt.plusSeconds(1), recoveredAt);
        assertTrue(repository.searchSyncQueue().hasPendingSearchSyncEvents(recoveredAt));

        Instant retryAt = recoveredAt.plusSeconds(90);
        List<MaterialSearchSyncQueueEntry> reclaimed = repository.searchSyncQueue().claimNextSearchSyncBatch(recoveredAt, 2);
        assertEquals(List.of(2, 2), reclaimed.stream().map(MaterialSearchSyncQueueEntry::attemptCount).sorted().toList());

        MaterialSearchSyncQueueEntry retryEntry = reclaimed.stream()
            .filter(entry -> entry.materialId().equals(firstRecord.id()))
            .findFirst()
            .orElseThrow();
        MaterialSearchSyncQueueEntry failedEntry = reclaimed.stream()
            .filter(entry -> entry.materialId().equals(secondRecord.id()))
            .findFirst()
            .orElseThrow();

        repository.searchSyncQueue().markSearchSyncEntryForRetry(
            retryEntry.materialId(),
            retryEntry.claimedAt(),
            "search.sync_failed",
            "Retry later",
            recoveredAt.plusSeconds(1),
            retryAt
        );
        repository.searchSyncQueue().markSearchSyncEntryFailed(
            failedEntry.materialId(),
            failedEntry.claimedAt(),
            "search.sync_failed",
            "Terminal failure",
            recoveredAt.plusSeconds(1)
        );

        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot retrySnapshot = repository.searchSyncQueue().getSearchSyncQueueSnapshot();
        assertEquals(1, retrySnapshot.pendingCount());
        assertEquals(0, retrySnapshot.inProgressCount());
        assertEquals(1, retrySnapshot.failedCount());
        assertEquals(retryAt, retrySnapshot.nextRetryAt());
        assertFalse(repository.searchSyncQueue().hasPendingSearchSyncEvents(retryAt.minusSeconds(1)));
        assertTrue(repository.searchSyncQueue().hasPendingSearchSyncEvents(retryAt));

        List<MaterialSearchSyncQueueEntry> retryClaim = repository.searchSyncQueue().claimNextSearchSyncBatch(retryAt, 1);
        assertEquals(firstRecord.id(), retryClaim.getFirst().materialId());
        assertEquals(3, retryClaim.getFirst().attemptCount());

        repository.searchSyncQueue().completeSearchSyncEntry(
            retryClaim.getFirst().materialId(),
            retryClaim.getFirst().claimedAt(),
            retryAt.plusSeconds(1)
        );

        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot deliveredSnapshot = repository.searchSyncQueue().getSearchSyncQueueSnapshot();
        assertEquals(0, deliveredSnapshot.pendingCount());
        assertEquals(0, deliveredSnapshot.inProgressCount());
        assertEquals(1, deliveredSnapshot.failedCount());
    }

    @Test
    void keepsWakeUpWhenMaterialIsReenqueuedDuringInProgressClaim() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Queue wake-up is preserved during in-flight claim.",
            "hash-search-sync-reenqueue",
            MaterialIndexingStatus.READY
        );
        saveReadyMaterial(record, "Queue wake-up is preserved during in-flight claim.");

        Instant createdAt = Instant.parse("2026-04-17T11:00:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        Instant reenqueueAt = createdAt.plusSeconds(20);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), reenqueueAt);
        repository.searchSyncQueue().completeSearchSyncEntry(record.id(), claimedEntry.claimedAt(), reenqueueAt.plusSeconds(1));

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(reenqueueAt, requeuedEntry.requestedAt());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
        assertEquals(null, requeuedEntry.lastErrorCode());
        assertEquals(null, requeuedEntry.lastErrorMessage());
        assertTrue(repository.searchSyncQueue().hasPendingSearchSyncEvents(reenqueueAt.plusSeconds(1)));
    }

    @Test
    void deleteIntentDuringInProgressUpsertIsPreservedAfterOldClaimCompletes() {
        StoredMaterialRecord record = searchSyncReadyRecord("Delete After Upsert", "hash-search-sync-delete-after-upsert");
        Instant createdAt = Instant.parse("2026-04-17T11:05:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        Instant deleteRequestedAt = createdAt.plusSeconds(20);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, deleteRequestedAt);
        repository.searchSyncQueue().completeSearchSyncEntry(record.id(), claimedEntry.claimedAt(), deleteRequestedAt.plusSeconds(1));

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.DELETE, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(deleteRequestedAt, requeuedEntry.requestedAt());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
        assertEquals(null, requeuedEntry.lastErrorCode());
        assertEquals(null, requeuedEntry.lastErrorMessage());
    }

    @Test
    void upsertIntentDuringInProgressDeleteIsPreservedAfterOldClaimCompletes() {
        StoredMaterialRecord record = searchSyncReadyRecord("Upsert After Delete", "hash-search-sync-upsert-after-delete");
        Instant createdAt = Instant.parse("2026-04-17T11:06:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        Instant upsertRequestedAt = createdAt.plusSeconds(20);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, upsertRequestedAt);
        repository.searchSyncQueue().completeSearchSyncEntry(record.id(), claimedEntry.claimedAt(), upsertRequestedAt.plusSeconds(1));

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.UPSERT, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(upsertRequestedAt, requeuedEntry.requestedAt());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
    }

    @Test
    void equalRequestedAtStillPreservesNewIntentDuringInProgressClaim() {
        StoredMaterialRecord record = searchSyncReadyRecord("Equal Timestamp", "hash-search-sync-equal-timestamp");
        Instant createdAt = Instant.parse("2026-04-17T11:07:00Z");
        Instant claimAt = createdAt.plusSeconds(5);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(claimAt, 1).getFirst();

        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, claimAt);
        repository.searchSyncQueue().completeSearchSyncEntry(record.id(), claimedEntry.claimedAt(), claimAt.plusSeconds(1));

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.DELETE, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(claimAt, requeuedEntry.requestedAt());
    }

    @Test
    void multipleIntentsDuringInProgressClaimKeepLatestOperation() {
        StoredMaterialRecord record = searchSyncReadyRecord("Latest Intent", "hash-search-sync-latest-intent");
        Instant createdAt = Instant.parse("2026-04-17T11:08:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, createdAt.plusSeconds(20));
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt.plusSeconds(21));
        repository.searchSyncQueue().completeSearchSyncEntry(record.id(), claimedEntry.claimedAt(), createdAt.plusSeconds(22));

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.UPSERT, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(createdAt.plusSeconds(21), requeuedEntry.requestedAt());
    }

    @Test
    void failedOldClaimDoesNotFailNewerIntent() {
        StoredMaterialRecord record = searchSyncReadyRecord("Fail Old Claim", "hash-search-sync-fail-old-claim");
        Instant createdAt = Instant.parse("2026-04-17T11:09:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        Instant deleteRequestedAt = createdAt.plusSeconds(20);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, deleteRequestedAt);
        repository.searchSyncQueue().markSearchSyncEntryFailed(
            record.id(),
            claimedEntry.claimedAt(),
            "search.sync_failed",
            "Old claim failed",
            deleteRequestedAt.plusSeconds(1)
        );

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.DELETE, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
        assertEquals(null, requeuedEntry.lastErrorCode());
        assertEquals(null, requeuedEntry.lastErrorMessage());
    }

    @Test
    void retryOldClaimDoesNotBackoffNewerIntent() {
        StoredMaterialRecord record = searchSyncReadyRecord("Retry Old Claim", "hash-search-sync-retry-old-claim");
        Instant createdAt = Instant.parse("2026-04-17T11:10:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        Instant deleteRequestedAt = createdAt.plusSeconds(20);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, deleteRequestedAt);
        repository.searchSyncQueue().markSearchSyncEntryForRetry(
            record.id(),
            claimedEntry.claimedAt(),
            "search.sync_failed",
            "Old claim failed",
            deleteRequestedAt.plusSeconds(1),
            deleteRequestedAt.plusSeconds(120)
        );

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.DELETE, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
        assertTrue(repository.searchSyncQueue().hasPendingSearchSyncEvents(deleteRequestedAt.plusSeconds(1)));
    }

    @Test
    void staleOldClaimCleanlyRequeuesNewerIntent() {
        StoredMaterialRecord record = searchSyncReadyRecord("Stale Old Claim", "hash-search-sync-stale-old-claim");
        Instant createdAt = Instant.parse("2026-04-17T11:11:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.UPSERT, createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();

        Instant deleteRequestedAt = createdAt.plusSeconds(20);
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), SearchSyncOperationType.DELETE, deleteRequestedAt);
        repository.searchSyncQueue().resetExpiredSearchSyncClaims(claimedEntry.claimedAt().plusSeconds(1), deleteRequestedAt.plusSeconds(1));

        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncOperationType.DELETE, requeuedEntry.operationType());
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
        assertEquals(null, requeuedEntry.lastErrorCode());
        assertEquals(null, requeuedEntry.lastErrorMessage());
    }

    @Test
    void requeuesFailedSearchSyncEntriesIntoPendingDelivery() {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "Terminally failed sync entry.",
            "hash-search-sync-requeue",
            MaterialIndexingStatus.READY
        );
        saveReadyMaterial(record, "Terminally failed sync entry.");

        Instant createdAt = Instant.parse("2026-04-17T11:30:00Z");
        repository.searchSyncQueue().enqueueMaterialsForSync(List.of(record.id()), createdAt);
        MaterialSearchSyncQueueEntry claimedEntry = repository.searchSyncQueue().claimNextSearchSyncBatch(createdAt.plusSeconds(5), 1).getFirst();
        repository.searchSyncQueue().markSearchSyncEntryFailed(
            claimedEntry.materialId(),
            claimedEntry.claimedAt(),
            "search.sync_failed",
            "Terminal failure",
            createdAt.plusSeconds(10)
        );

        int requeued = repository.searchSyncQueue().requeueFailedSearchSyncEntries(createdAt.plusSeconds(20));

        assertEquals(1, requeued);
        MaterialSearchSyncQueueEntry requeuedEntry = repository.searchSyncQueue().findAllSearchSyncEntries().getFirst();
        assertEquals(SearchSyncDeliveryState.PENDING, requeuedEntry.deliveryState());
        assertEquals(0, requeuedEntry.attemptCount());
        assertEquals(null, requeuedEntry.nextAttemptAt());
        assertEquals(null, requeuedEntry.claimedAt());
        assertEquals(null, requeuedEntry.lastErrorCode());
        assertEquals(null, requeuedEntry.lastErrorMessage());
        assertTrue(repository.searchSyncQueue().hasPendingSearchSyncEvents(createdAt.plusSeconds(20)));
    }

    @Test
    void resolvesSearchableSnapshotAcrossLifecycleStates() {
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        StoredMaterialRecord ready = materialRecord(
            UUID.randomUUID().toString(),
            "Ready FAQ",
            "Готовая версия",
            "hash-snapshot-ready",
            "snapshot-lineage-ready",
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            baseTime
        );
        StoredMaterialRecord partialReady = materialRecord(
            UUID.randomUUID().toString(),
            "Partial FAQ",
            "Частично готовая версия",
            "hash-snapshot-partial",
            "snapshot-lineage-partial",
            MaterialIndexingStatus.PARTIAL_READY,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(30)
        );
        StoredMaterialRecord supersededReady = materialRecord(
            UUID.randomUUID().toString(),
            "Superseded FAQ",
            "Историческая версия",
            "hash-snapshot-superseded",
            "snapshot-lineage-superseded",
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime.plusSeconds(60)
        );
        StoredMaterialRecord pending = materialRecord(
            UUID.randomUUID().toString(),
            "Pending FAQ",
            "Индекс в очереди",
            "hash-snapshot-pending",
            "snapshot-lineage-pending",
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(90)
        );
        StoredMaterialRecord failed = materialRecord(
            UUID.randomUUID().toString(),
            "Failed FAQ",
            "Индекс упал",
            "hash-snapshot-failed",
            "snapshot-lineage-failed",
            MaterialIndexingStatus.FAILED,
            MaterialVersionState.ACTIVE,
            baseTime.plusSeconds(120)
        );

        saveReadyMaterial(ready, "Готовая версия");
        saveReadyMaterial(partialReady, "Частично готовая версия");
        saveReadyMaterial(supersededReady, "Историческая версия");
        repository.catalog().save(pending, List.of(rawChunk(0, "Индекс в очереди", 1)));
        repository.catalog().save(failed, List.of(rawChunk(0, "Индекс упал", 1)));

        SearchableMaterialSnapshot readySnapshot = repository.searchableSnapshot().resolveSearchableSnapshot(ready.id());
        SearchableMaterialSnapshot partialSnapshot = repository.searchableSnapshot().resolveSearchableSnapshot(partialReady.id());
        SearchableMaterialSnapshot supersededSnapshot = repository.searchableSnapshot().resolveSearchableSnapshot(supersededReady.id());
        SearchableMaterialSnapshot pendingSnapshot = repository.searchableSnapshot().resolveSearchableSnapshot(pending.id());
        SearchableMaterialSnapshot failedSnapshot = repository.searchableSnapshot().resolveSearchableSnapshot(failed.id());

        assertTrue(readySnapshot.searchable());
        assertEquals("Ready FAQ", readySnapshot.title());
        assertEquals(1, readySnapshot.chunks().size());
        assertTrue(partialSnapshot.searchable());
        assertEquals("Partial FAQ", partialSnapshot.title());
        assertFalse(supersededSnapshot.searchable());
        assertFalse(pendingSnapshot.searchable());
        assertFalse(failedSnapshot.searchable());
        assertEquals(
            List.of(ready.id(), partialReady.id()),
            repository.searchableSnapshot().findAllSearchableMaterialIds()
        );
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
        repository.catalog().save(activePending, List.of(rawChunk(0, "currentgamma support plan is being reindexed.", 1)));

        assertEquals(2, repository.catalog().countMaterials());
        assertEquals(1, repository.catalog().countActiveMaterials());
        assertEquals(0, repository.catalog().countReadyMaterials());
        assertTrue(repository.lexical().search("legacybeta", 5).isEmpty());
        assertTrue(repository.semantic().searchSemantic(embeddingClient.embed("legacybeta"), 5).isEmpty());
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

        List<MaterialChunkSearchMatch> lexicalMatches = repository.lexical().search("legacybeta", 5);
        assertTrue(lexicalMatches.isEmpty());

        List<MaterialChunkSearchMatch> semanticMatches = repository.semantic().searchSemantic(
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
            MaterialVersionState.SUPERSEDED,
            baseTime.plusSeconds(60)
        );
        StoredMaterialRecord v3 = materialRecord(
            UUID.randomUUID().toString(),
            "Pricing FAQ",
            "lineage-gamma support plan costs 15000 tenge.",
            "hash-lineage-v3",
            sourceKey,
            MaterialIndexingStatus.READY,
            MaterialVersionState.SUPERSEDED,
            baseTime.plusSeconds(120)
        );

        saveReadyMaterial(v1, "lineage-alpha support plan costs 9000 tenge.");
        saveReadyMaterial(v2, "lineage-beta support plan costs 12000 tenge.");
        repository.catalog().supersedeActiveVersions(
            sourceKey,
            v2.id(),
            v2.id(),
            "material.superseded_by_new_active_version",
            baseTime.plusSeconds(90)
        );
        repository.catalog().updateVersionState(v2.id(), MaterialVersionState.ACTIVE, null, null, baseTime.plusSeconds(91));

        saveReadyMaterial(v3, "lineage-gamma support plan costs 15000 tenge.");
        repository.catalog().supersedeActiveVersions(
            sourceKey,
            v3.id(),
            v3.id(),
            "material.superseded_by_new_active_version",
            baseTime.plusSeconds(150)
        );
        repository.catalog().updateVersionState(v3.id(), MaterialVersionState.ACTIVE, null, null, baseTime.plusSeconds(151));

        assertEquals(3, repository.catalog().countMaterials());
        assertEquals(1, repository.catalog().countActiveMaterials());
        assertEquals(1, repository.catalog().countReadyMaterials());
        assertEquals(
            v2.id(),
            repository.catalog().findLatestBySourceKeyAndVersionState(sourceKey, MaterialVersionState.SUPERSEDED).orElseThrow().id()
        );
        assertTrue(repository.lexical().search("lineage-beta", 5).isEmpty());
        assertEquals(v3.id(), repository.semantic().searchSemantic(embeddingClient.embed("lineage-beta"), 5).getFirst().materialId());

        repository.catalog().updateVersionState(v3.id(), MaterialVersionState.SUPERSEDED, v2.id(), "material.manual_promotion", baseTime.plusSeconds(180));
        repository.catalog().updateVersionState(v2.id(), MaterialVersionState.ACTIVE, null, null, baseTime.plusSeconds(181));

        assertEquals(1, repository.catalog().countActiveMaterials());
        assertEquals(1, repository.catalog().countReadyMaterials());
        assertTrue(repository.lexical().search("lineage-gamma", 5).isEmpty());
        assertEquals(v2.id(), repository.lexical().search("lineage-beta", 5).getFirst().materialId());
        assertEquals(v2.id(), repository.semantic().searchSemantic(embeddingClient.embed("lineage-beta"), 5).getFirst().materialId());
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

    private MaterialMetadataSnapshot retrievalMetadata(DocumentStatus documentStatus, LocalDate periodStart, LocalDate periodEnd) {
        return MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.POLICY,
            "general",
            documentStatus,
            null,
            null,
            null,
            List.of(),
            periodStart,
            periodEnd,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
    }

    private StoredMaterialRecord searchSyncReadyRecord(String title, String contentHash) {
        StoredMaterialRecord record = materialRecord(
            UUID.randomUUID().toString(),
            title,
            title + " searchable content.",
            contentHash,
            MaterialIndexingStatus.READY
        );
        saveReadyMaterial(record, record.content());
        return record;
    }

    private StoredMaterialChunk rawChunk(int index, String text, Integer page) {
        return new StoredMaterialChunk(index, text, List.of(), page, "direct-text", false);
    }

    private List<String> sortedEntryMaterialIds(List<MaterialSearchSyncQueueEntry> entries) {
        return entries.stream()
            .map(MaterialSearchSyncQueueEntry::materialId)
            .sorted()
            .toList();
    }

    private List<String> sortedIds(String... materialIds) {
        return java.util.Arrays.stream(materialIds)
            .sorted()
            .toList();
    }

    private void seedWorkspace(String key, String nameRu) {
        jdbcTemplate.update(
            """
                INSERT INTO reference_workspaces (
                    key,
                    name_ru,
                    active,
                    sort_order,
                    is_default,
                    created_at,
                    updated_at
                ) VALUES (?, ?, true, 0, false, NOW(), NOW())
                ON CONFLICT (key) DO NOTHING
                """,
            key,
            nameRu
        );
    }

    private void seedProject(String key, String workspaceKey, String nameRu) {
        jdbcTemplate.update(
            """
                INSERT INTO reference_projects (
                    key,
                    workspace_key,
                    name_ru,
                    active,
                    sort_order,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, true, 0, NOW(), NOW())
                ON CONFLICT (key) DO NOTHING
                """,
            key,
            workspaceKey,
            nameRu
        );
    }

    private void saveReadyMaterial(StoredMaterialRecord record, String embeddedText) {
        repository.catalog().save(record, List.of(rawChunk(0, embeddedText, 1)));
        repository.indexingQueue().markIndexingReady(
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
