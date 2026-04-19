package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.infrastructure.material.LexicalProviderMode;
import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import com.example.demo.infrastructure.material.MaterialChunkingRepository;
import com.example.demo.infrastructure.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.infrastructure.material.DocumentBlockConfidence;
import com.example.demo.infrastructure.material.DocumentBlockType;
import com.example.demo.infrastructure.material.SemanticSearchRepository;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.ChatSource;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MaterialRetrievalServiceTest {

    @Test
    void rejectsSearchLimitAboveConfiguredHardCap() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialRetrievalService service = createService(repository, new DeterministicEmbeddingClient());

        ApiException exception = assertThrows(ApiException.class, () -> service.search(new MaterialSearchRequest(
            "проверка лимита",
            RetrievalFilters.empty(),
            21,
            false,
            false
        )));

        assertEquals("search.limit_too_large", exception.getCode());
    }

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

    @Test
    void keepsProductionResultStableWhenShadowComparisonIsEnabled() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        RecordingShadowComparisonService shadowComparisonService = new RecordingShadowComparisonService();
        MaterialRetrievalService service = createService(repository, embeddingClient, shadowComparisonService);

        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Какая цена тарифа Премиум?");

        assertFalse(result.sources().isEmpty());
        assertEquals(1, shadowComparisonService.calls);
        assertEquals("postgres", shadowComparisonService.providerType);
    }

    @Test
    void filtersMaterialsByDocumentClassAndWorkspaceKey() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveMaterial(
            repository,
            embeddingClient,
            "North contract",
            "Договор на модернизацию North Upgrade, цена 12000 тенге.",
            "north-contract-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z"),
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                null,
                null,
                null,
                null,
                null,
                null,
                "ru",
                List.of("finance"),
                null,
                "North Upgrade",
                null,
                null,
                null,
                null,
                null
            ))
        );
        saveMaterial(
            repository,
            embeddingClient,
            "Operations report",
            "Отчет по North Upgrade, цена 12000 тенге.",
            "north-report-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:05:00Z"),
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.REPORT,
                null,
                null,
                null,
                null,
                null,
                null,
                "ru",
                List.of("finance"),
                null,
                "North Upgrade",
                null,
                null,
                null,
                null,
                null
            ))
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "Какая цена North Upgrade?",
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), "north-upgrade", false)
        );

        assertEquals(2, result.materialCount());
        assertEquals(2, result.activeMaterialCount());
        assertEquals(2, result.readyMaterialCount());
        assertEquals(1, result.scopedMaterialCount());
        assertEquals(1, result.scopedActiveMaterialCount());
        assertEquals(1, result.scopedReadyMaterialCount());
        assertFalse(result.sources().isEmpty());
        assertTrue(result.sources().stream().allMatch(source -> source.title().equals("North contract")));
    }

    @Test
    void combinesKnowledgeScopeAndRetrievalFiltersAndReturnsChunkMetadata() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveStructuredMaterial(
            repository,
            embeddingClient,
            "North contract",
            "north-contract-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch", "grid"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(
                new StoredMaterialChunk(
                    0,
                    "Dispatch matrix for contract KZ-2026-0415-ENERGY approved for April 2026.",
                    List.of("dispatch", "matrix", "contract", "approved"),
                    2,
                    "structured-v1",
                    false,
                    DocumentBlockType.TABLE,
                    List.of("dispatch-overview", "dispatch-matrix"),
                    List.of("Dispatch overview", "Dispatch matrix"),
                    "table-1",
                    null,
                    DocumentBlockConfidence.HIGH
                )
            ),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        saveStructuredMaterial(
            repository,
            embeddingClient,
            "North report",
            "north-report-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch", "grid"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix summary for the North report.",
                List.of("dispatch", "matrix", "summary"),
                1,
                "structured-v1",
                false
            )),
            Instant.parse("2026-04-17T10:01:00Z")
        );
        saveStructuredMaterial(
            repository,
            embeddingClient,
            "South contract",
            "south-contract-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch", "grid"),
                SourceTrustLevel.LOW,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for a low-trust source.",
                List.of("dispatch", "matrix", "source"),
                1,
                "structured-v1",
                false
            )),
            Instant.parse("2026-04-17T10:02:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "dispatch matrix",
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), "north-upgrade", false),
            new RetrievalFilters(
                "KZ-2026-0415-ENERGY",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                "Grid operations",
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.MEDIUM
            )
        );

        assertEquals(3, result.materialCount());
        assertEquals(3, result.activeMaterialCount());
        assertEquals(3, result.readyMaterialCount());
        assertEquals(1, result.scopedMaterialCount());
        assertEquals(1, result.scopedActiveMaterialCount());
        assertEquals(1, result.scopedReadyMaterialCount());
        assertFalse(result.sources().isEmpty());
        ChatSource source = result.sources().getFirst();
        assertEquals("North contract", source.title());
        assertEquals(DocumentBlockType.TABLE, source.chunkType());
        assertEquals("KZ-2026-0415-ENERGY", source.metadata().documentNumber());
        assertEquals("North Upgrade", source.metadata().project());
        assertEquals(SourceTrustLevel.HIGH, source.metadata().sourceTrust());
    }

    @Test
    void keepsLegacyDerivedMetadataDiscoverableThroughKnowledgeScope() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveMaterial(
            repository,
            embeddingClient,
            "Legacy contract",
            "Исторический договор North Upgrade описывает ставку 9000 тенге.",
            "legacy-contract-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z"),
            new MaterialMetadataSnapshot(
                DocumentType.CONTRACT,
                null,
                null,
                null,
                null,
                null,
                null,
                "ru",
                List.of(),
                null,
                "North Upgrade",
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "Какая ставка в North Upgrade?",
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), "north-upgrade", false)
        );

        assertEquals(1, result.scopedMaterialCount());
        assertFalse(result.sources().isEmpty());
        assertEquals("Legacy contract", result.sources().getFirst().title());
    }

    @Test
    void rerankerPromotesIdentifierSensitiveChunkAndExposesScoreBreakdown() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveStructuredMaterial(
            repository,
            embeddingClient,
            "Archive appendix",
            "archive-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.OTHER,
                null,
                null,
                null,
                null,
                null,
                "ru",
                List.of(),
                SourceTrustLevel.UNKNOWN,
                null,
                null,
                null,
                null,
                null
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Приложение А. Архивные ставки для старых договоров.",
                List.of("приложение", "архивные", "договоры"),
                3,
                "structured-v1",
                false,
                DocumentBlockType.APPENDIX,
                List.of("appendix-a"),
                List.of("Appendix A"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        saveStructuredMaterial(
            repository,
            embeddingClient,
            "Dispatch matrix",
            "dispatch-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for contract KZ-2026-0415-ENERGY in project North Upgrade approved for April 2026.",
                List.of("dispatch", "matrix", "contract", "north", "upgrade", "approved"),
                1,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                List.of("dispatch", "matrix"),
                List.of("Dispatch", "Dispatch matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            Instant.parse("2026-04-17T10:01:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?"
        );

        assertFalse(result.sources().isEmpty());
        ChatSource topSource = result.sources().getFirst();
        assertEquals("Dispatch matrix", topSource.title());
        assertEquals(DocumentBlockType.TABLE, topSource.chunkType());
        assertTrue(topSource.scoreBreakdown() != null);
        assertTrue(topSource.scoreBreakdown().identifierBonus() >= 20);
        assertTrue(topSource.scoreBreakdown().metadataBonus() >= 4);
        assertEquals("KZ-2026-0415-ENERGY", result.retrievalDebug().queryHints().documentNumber());
        assertEquals("North Upgrade", result.retrievalDebug().queryHints().project());
        assertEquals("KZ-2026-0415-ENERGY", result.retrievalDebug().effectiveFilters().documentNumber());
    }

    @Test
    void ignoresDismissedRetrievalHintKeysBeforeMergingEffectiveFilters() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);
        saveStructuredMaterial(
            repository,
            embeddingClient,
            "Dispatch matrix",
            "dispatch-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for contract KZ-2026-0415-ENERGY in project North Upgrade approved for April 2026.",
                List.of("dispatch", "matrix", "contract", "north", "upgrade", "approved"),
                1,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                List.of("dispatch", "matrix"),
                List.of("Dispatch", "Dispatch matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            Instant.parse("2026-04-17T10:01:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?",
            KnowledgeScope.empty(),
            RetrievalFilters.empty(),
            List.of("documentNumber", "project")
        );

        assertFalse(result.sources().isEmpty());
        assertEquals(null, result.retrievalDebug().queryHints().documentNumber());
        assertEquals(null, result.retrievalDebug().queryHints().project());
        assertEquals(null, result.retrievalDebug().effectiveFilters().documentNumber());
        assertEquals(null, result.retrievalDebug().effectiveFilters().project());
    }

    @Test
    void combinesManualFiltersAndKnowledgeScopeWithRetrievalDebug() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveStructuredMaterial(
            repository,
            embeddingClient,
            "North dispatch contract",
            "north-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for North Upgrade is approved.",
                List.of("dispatch", "north", "upgrade"),
                1,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                List.of("dispatch"),
                List.of("Dispatch matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        saveStructuredMaterial(
            repository,
            embeddingClient,
            "South dispatch contract",
            "south-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-16"),
                "SOUTH-2026-0416",
                "Aruzhan Imanova",
                "South operations",
                "v1",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.HIGH,
                "South Upgrade",
                "SouthGrid LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for South Upgrade is approved.",
                List.of("dispatch", "south", "upgrade"),
                1,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                List.of("dispatch"),
                List.of("Dispatch matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            Instant.parse("2026-04-17T10:01:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "dispatch matrix",
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), null, false),
            new RetrievalFilters(
                null,
                null,
                null,
                "Grid operations",
                "North Upgrade",
                null,
                null,
                "ru",
                List.of(),
                null
            )
        );

        assertEquals(1, result.scopedReadyMaterialCount());
        assertEquals("Grid operations", result.retrievalDebug().manualFilters().department());
        assertEquals("North Upgrade", result.retrievalDebug().effectiveFilters().project());
        assertEquals("hybrid-rerank-v1", result.retrievalDebug().relevanceProfile());
        assertEquals(1, result.sources().size());
    }

    @Test
    void suppressesQueryHintsWhenQueryHintsRolloutIsDisabled() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setQueryHintsV1(false);
        MaterialRetrievalService service = createService(repository, embeddingClient, rolloutProperties);

        saveStructuredMaterial(
            repository,
            embeddingClient,
            "North dispatch contract",
            "north-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(new StoredMaterialChunk(
                0,
                "Dispatch matrix for North Upgrade is approved.",
                List.of("dispatch", "north", "upgrade"),
                1,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                List.of("dispatch"),
                List.of("Dispatch matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            Instant.parse("2026-04-17T10:00:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "договор KZ-2026-0415-ENERGY по проекту North Upgrade",
            KnowledgeScope.empty(),
            new RetrievalFilters(
                null,
                null,
                null,
                "Grid operations",
                "North Upgrade",
                null,
                null,
                null,
                List.of(),
                null
            )
        );

        assertTrue(result.retrievalDebug().queryHints().equals(com.example.demo.model.RetrievalQueryHints.empty()));
        assertEquals("North Upgrade", result.retrievalDebug().effectiveFilters().project());
        assertFalse(result.retrievalDebug().appliedCapabilities().contains("query-hints-v1"));
    }

    @Test
    void forcesHybridProfileWhenRerankerRolloutIsDisabled() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setRerankerV1(false);
        MaterialRetrievalService service = createService(repository, embeddingClient, rolloutProperties);

        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Сколько стоит тариф Премиум?");

        assertEquals("hybrid-v1", result.retrievalDebug().relevanceProfile());
        assertFalse(result.retrievalDebug().appliedCapabilities().contains("reranker-v1"));
        assertTrue(result.sources().stream().allMatch(source -> source.scoreBreakdown() == null));
    }

    @Test
    void suppressesManualFiltersWhenMetadataFilterRolloutIsDisabled() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setMetadataFiltersV1(false);
        rolloutProperties.setQueryHintsV1(true);
        MaterialRetrievalService service = createService(repository, embeddingClient, rolloutProperties);

        saveMaterial(
            repository,
            embeddingClient,
            "North dispatch",
            "North Upgrade dispatch approved.",
            "north-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z"),
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                null,
                null,
                "Dana Sarsen",
                "Grid operations",
                null,
                "ru",
                List.of("dispatch"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                null,
                null,
                null,
                null
            ))
        );

        MaterialRetrievalResult result = service.retrieveContext(
            "dispatch",
            KnowledgeScope.empty(),
            new RetrievalFilters(
                null,
                null,
                null,
                "South operations",
                null,
                null,
                null,
                null,
                List.of(),
                null
            )
        );

        assertFalse(result.sources().isEmpty());
        assertTrue(result.retrievalDebug().effectiveFilters().isEmpty());
        assertEquals("South operations", result.retrievalDebug().manualFilters().department());
        assertFalse(result.retrievalDebug().appliedCapabilities().contains("metadata-filters-v1"));
        assertTrue(result.retrievalDebug().suppressedCapabilities().contains("metadata-filters-v1"));
        assertTrue(result.retrievalDebug().suppressedCapabilities().contains("query-hints-v1"));
        assertFalse(result.retrievalDebug().activeRolloutFlags().metadataFiltersV1());
        assertTrue(result.retrievalDebug().activeRolloutFlags().queryHintsV1());
    }

    @Test
    void buildsChunkAwareOpenSourceUrl() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService service = createService(repository, embeddingClient);

        saveMaterial(
            repository,
            embeddingClient,
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );

        MaterialRetrievalResult result = service.retrieveContext("Сколько стоит тариф Премиум?");

        assertFalse(result.sources().isEmpty());
        assertEquals(
            "/api/materials/" + result.sources().getFirst().materialId()
                + "?chunkId=" + result.sources().getFirst().chunkId().replace(":", "%3A")
                + "&chunkIndex=0",
            result.sources().getFirst().openSourceUrl()
        );
    }

    @Test
    void passesScopedReadyMaterialIdsIntoSearchLayers() {
        MaterialCatalogRepository catalogRepository = Mockito.mock(MaterialCatalogRepository.class);
        SemanticSearchRepository semanticSearchRepository = Mockito.mock(SemanticSearchRepository.class);
        ProductionLexicalSearchRouter lexicalSearchRouter = Mockito.mock(ProductionLexicalSearchRouter.class);
        StoredMaterialRecord allowedRecord = readyRecord(
            "North contract",
            "Договор North Upgrade с тарифом 12000 тенге.",
            "north-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                null,
                null,
                null,
                null,
                null,
                null,
                "ru",
                List.of("finance"),
                null,
                "North Upgrade",
                null,
                null,
                null,
                null,
                null
            ))
        );
        StoredMaterialRecord excludedRecord = readyRecord(
            "Ops report",
            "Отчёт South Upgrade с тарифом 9000 тенге.",
            "south-lineage",
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.REPORT,
                null,
                null,
                null,
                null,
                null,
                null,
                "ru",
                List.of("finance"),
                null,
                "South Upgrade",
                null,
                null,
                null,
                null,
                null
            ))
        );
        when(catalogRepository.describeRetrievalScope(any(), any(), any(), any())).thenReturn(scopeSnapshot(
            List.of(allowedRecord, excludedRecord),
            List.of(allowedRecord)
        ));
        when(catalogRepository.findById(allowedRecord.id())).thenReturn(java.util.Optional.of(allowedRecord));
        when(semanticSearchRepository.searchSemantic(any(), anyInt(), anySet(), eq(RetrievalFilters.empty())))
            .thenReturn(List.of(matchFor(allowedRecord, 0.12d, null)));
        when(lexicalSearchRouter.search(any(), anyInt(), anySet(), eq(RetrievalFilters.empty()))).thenReturn(new ProductionLexicalSearchRouter.LexicalSearchResult(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            "search.sync_disabled",
            "Elasticsearch search sync is disabled by configuration.",
            null,
            List.of(matchFor(allowedRecord, 0.12d, 0.9d))
        ));

        MaterialRetrievalService service = createMockDrivenService(catalogRepository, semanticSearchRepository, lexicalSearchRouter);

        MaterialRetrievalResult result = service.retrieveContext(
            "Какая цена North Upgrade?",
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), "north-upgrade", false)
        );

        verify(semanticSearchRepository).searchSemantic(any(), eq(12), eq(Set.of(allowedRecord.id())), eq(RetrievalFilters.empty()));
        verify(lexicalSearchRouter).search(
            eq("Какая цена North Upgrade?"),
            eq(12),
            eq(Set.of(allowedRecord.id())),
            eq(RetrievalFilters.empty())
        );
        verify(catalogRepository, never()).findAll();
        assertEquals(1, result.scopedMaterialCount());
        assertEquals(1, result.scopedReadyMaterialCount());
        assertTrue(result.sources().stream().allMatch(source -> source.materialId().equals(allowedRecord.id())));
    }

    @Test
    void classifiesSupportVerdictAsNoneWhenNoRankedChunksSurvive() {
        MaterialCatalogRepository catalogRepository = Mockito.mock(MaterialCatalogRepository.class);
        SemanticSearchRepository semanticSearchRepository = Mockito.mock(SemanticSearchRepository.class);
        ProductionLexicalSearchRouter lexicalSearchRouter = Mockito.mock(ProductionLexicalSearchRouter.class);
        StoredMaterialRecord record = readyRecord("Policy", "Внутренний регламент.", "policy-lineage", MaterialMetadataSnapshot.empty());
        when(catalogRepository.describeRetrievalScope(any(), any(), any(), any())).thenReturn(scopeSnapshot(
            List.of(record),
            List.of(record)
        ));
        when(catalogRepository.findById(record.id())).thenReturn(java.util.Optional.of(record));
        when(semanticSearchRepository.searchSemantic(any(), anyInt(), anySet(), eq(RetrievalFilters.empty()))).thenReturn(List.of());
        when(lexicalSearchRouter.search(any(), anyInt(), anySet(), eq(RetrievalFilters.empty()))).thenReturn(new ProductionLexicalSearchRouter.LexicalSearchResult(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            "search.sync_disabled",
            "Elasticsearch search sync is disabled by configuration.",
            null,
            List.of()
        ));

        MaterialRetrievalService service = createMockDrivenService(catalogRepository, semanticSearchRepository, lexicalSearchRouter);

        MaterialRetrievalResult result = service.retrieveContext("Какая ставка?");

        assertEquals("none", result.retrievalTrace().supportVerdict());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void classifiesSupportVerdictAsWeakWhenOnlyOneSearchChannelFindsEvidence() {
        MaterialCatalogRepository catalogRepository = Mockito.mock(MaterialCatalogRepository.class);
        SemanticSearchRepository semanticSearchRepository = Mockito.mock(SemanticSearchRepository.class);
        ProductionLexicalSearchRouter lexicalSearchRouter = Mockito.mock(ProductionLexicalSearchRouter.class);
        StoredMaterialRecord record = readyRecord("Pricing FAQ", "Тариф Премиум стоит 12000 тенге.", "pricing-lineage", MaterialMetadataSnapshot.empty());
        when(catalogRepository.describeRetrievalScope(any(), any(), any(), any())).thenReturn(scopeSnapshot(
            List.of(record),
            List.of(record)
        ));
        when(catalogRepository.findById(record.id())).thenReturn(java.util.Optional.of(record));
        when(semanticSearchRepository.searchSemantic(any(), anyInt(), anySet(), eq(RetrievalFilters.empty())))
            .thenReturn(List.of(matchFor(record, 0.18d, null)));
        when(lexicalSearchRouter.search(any(), anyInt(), anySet(), eq(RetrievalFilters.empty()))).thenReturn(new ProductionLexicalSearchRouter.LexicalSearchResult(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            "search.sync_disabled",
            "Elasticsearch search sync is disabled by configuration.",
            null,
            List.of()
        ));

        MaterialRetrievalService service = createMockDrivenService(catalogRepository, semanticSearchRepository, lexicalSearchRouter);

        MaterialRetrievalResult result = service.retrieveContext("Сколько стоит тариф Премиум?");

        assertEquals("weak", result.retrievalTrace().supportVerdict());
        assertFalse(result.sources().isEmpty());
    }

    @Test
    void classifiesSupportVerdictAsSufficientWhenSemanticAndLexicalEvidenceAgree() {
        MaterialCatalogRepository catalogRepository = Mockito.mock(MaterialCatalogRepository.class);
        SemanticSearchRepository semanticSearchRepository = Mockito.mock(SemanticSearchRepository.class);
        ProductionLexicalSearchRouter lexicalSearchRouter = Mockito.mock(ProductionLexicalSearchRouter.class);
        StoredMaterialRecord record = readyRecord("Pricing FAQ", "Тариф Премиум стоит 12000 тенге.", "pricing-lineage", MaterialMetadataSnapshot.empty());
        MaterialChunkSearchMatch corroboratedMatch = matchFor(record, 0.09d, 0.95d);
        when(catalogRepository.describeRetrievalScope(any(), any(), any(), any())).thenReturn(scopeSnapshot(
            List.of(record),
            List.of(record)
        ));
        when(catalogRepository.findById(record.id())).thenReturn(java.util.Optional.of(record));
        when(semanticSearchRepository.searchSemantic(any(), anyInt(), anySet(), eq(RetrievalFilters.empty())))
            .thenReturn(List.of(corroboratedMatch));
        when(lexicalSearchRouter.search(any(), anyInt(), anySet(), eq(RetrievalFilters.empty()))).thenReturn(new ProductionLexicalSearchRouter.LexicalSearchResult(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            "search.sync_disabled",
            "Elasticsearch search sync is disabled by configuration.",
            null,
            List.of(corroboratedMatch)
        ));

        MaterialRetrievalService service = createMockDrivenService(catalogRepository, semanticSearchRepository, lexicalSearchRouter);

        MaterialRetrievalResult result = service.retrieveContext("Сколько стоит тариф Премиум?");

        assertEquals("sufficient", result.retrievalTrace().supportVerdict());
        assertFalse(result.sources().isEmpty());
    }

    private MaterialRetrievalService createService(
        InMemoryMaterialRepository repository,
        DeterministicEmbeddingClient embeddingClient
    ) {
        return createService(repository, embeddingClient, (query, productionProvider, productionMatches, limit) -> {
        });
    }

    private MaterialRetrievalService createService(
        InMemoryMaterialRepository repository,
        DeterministicEmbeddingClient embeddingClient,
        RolloutProperties rolloutProperties
    ) {
        MaterialProperties properties = new MaterialProperties();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        RagProperties ragProperties = new RagProperties();
        return new MaterialRetrievalService(
            repository,
            repository,
            repository,
            TestLexicalRoutingSupport.productionRouter(repository, ragProperties, List.of(repository)),
            embeddingClient,
            ragProperties,
            new HybridChunkRanker(),
            new ChunkReranker(contentSupport),
            contentSupport,
            new RetrievalQueryHintExtractor(),
            (query, productionProvider, productionMatches, limit) -> {
            },
            new AnswerModePostProcessor(contentSupport),
            rolloutProperties,
            QualityLayerHealthService.noop(rolloutProperties)
        );
    }

    private MaterialRetrievalService createService(
        InMemoryMaterialRepository repository,
        DeterministicEmbeddingClient embeddingClient,
        LexicalShadowComparisonService shadowComparisonService
    ) {
        MaterialProperties properties = new MaterialProperties();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        RagProperties ragProperties = new RagProperties();
        return TestMaterialServices.retrievalService(
            repository,
            repository,
            repository,
            TestLexicalRoutingSupport.productionRouter(repository, ragProperties, List.of(repository)),
            embeddingClient,
            ragProperties,
            new HybridChunkRanker(),
            contentSupport,
            shadowComparisonService
        );
    }

    private MaterialRetrievalService createMockDrivenService(
        MaterialCatalogRepository catalogRepository,
        SemanticSearchRepository semanticSearchRepository,
        ProductionLexicalSearchRouter lexicalSearchRouter
    ) {
        MaterialProperties properties = new MaterialProperties();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        RagProperties ragProperties = new RagProperties();
        EmbeddingClient embeddingClient = new EmbeddingClient() {
            @Override
            public float[] embed(String input) {
                return new float[] {1.0f, 0.0f, 0.0f};
            }

            @Override
            public List<float[]> embedAll(List<String> inputs) {
                return inputs.stream()
                    .map(ignored -> new float[] {1.0f, 0.0f, 0.0f})
                    .toList();
            }
        };
        MaterialChunkingRepository chunkingRepository = Mockito.mock(MaterialChunkingRepository.class);
        return TestMaterialServices.retrievalService(
            catalogRepository,
            chunkingRepository,
            semanticSearchRepository,
            lexicalSearchRouter,
            embeddingClient,
            ragProperties,
            new HybridChunkRanker(),
            contentSupport,
            (query, productionProvider, productionMatches, limit) -> {
            }
        );
    }

    private MaterialRetrievalScopeSnapshot scopeSnapshot(
        List<StoredMaterialRecord> allRecords,
        List<StoredMaterialRecord> scopedRecords
    ) {
        return new MaterialRetrievalScopeSnapshot(
            allRecords.size(),
            (int) allRecords.stream().filter(record -> record.versionState() == MaterialVersionState.ACTIVE).count(),
            (int) allRecords.stream().filter(this::isReadyForRetrieval).count(),
            scopedRecords.size(),
            (int) scopedRecords.stream().filter(record -> record.versionState() == MaterialVersionState.ACTIVE).count(),
            (int) scopedRecords.stream().filter(this::isReadyForRetrieval).count(),
            scopedRecords.stream()
                .filter(this::isReadyForRetrieval)
                .map(StoredMaterialRecord::id)
                .collect(java.util.stream.Collectors.toSet())
        );
    }

    private boolean isReadyForRetrieval(StoredMaterialRecord record) {
        return record.versionState() == MaterialVersionState.ACTIVE
            && (record.status() == MaterialIndexingStatus.READY || record.status() == MaterialIndexingStatus.PARTIAL_READY);
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
        return saveMaterial(
            repository,
            embeddingClient,
            title,
            content,
            sourceKey,
            versionState,
            status,
            timestamp,
            MaterialMetadataSnapshot.empty()
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
        Instant timestamp,
        MaterialMetadataSnapshot metadata
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
            timestamp,
            metadata
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

    private StoredMaterialRecord readyRecord(
        String title,
        String content,
        String sourceKey,
        MaterialMetadataSnapshot metadata
    ) {
        Instant timestamp = Instant.parse("2026-04-17T10:00:00Z");
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
            List.of(new StoredMaterialChunk(
                0,
                content,
                List.of(),
                null,
                "direct-text",
                false
            )),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            timestamp,
            timestamp,
            metadata
        );
    }

    private MaterialChunkSearchMatch matchFor(StoredMaterialRecord record, Double semanticDistance, Double lexicalScore) {
        return new MaterialChunkSearchMatch(
            record.id(),
            0,
            record.title(),
            record.chunks().getFirst().text(),
            null,
            "direct-text",
            false,
            semanticDistance,
            lexicalScore
        );
    }

    private StoredMaterialRecord saveStructuredMaterial(
        InMemoryMaterialRepository repository,
        DeterministicEmbeddingClient embeddingClient,
        String title,
        String sourceKey,
        MaterialMetadataSnapshot metadata,
        List<StoredMaterialChunk> chunks,
        Instant timestamp
    ) {
        String content = chunks.stream()
            .map(StoredMaterialChunk::text)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
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
            "structured-v1",
            false,
            null,
            chunks,
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            timestamp,
            timestamp,
            metadata
        );

        repository.save(record, "structured-v1", chunks, List.of());
        repository.markIndexingReady(
            record.id(),
            chunks.stream()
                .map(chunk -> new StoredEmbeddedMaterialChunk(
                    chunk.index(),
                    chunk.text(),
                    chunk.page(),
                    chunk.extractor(),
                    Boolean.TRUE.equals(chunk.ocrUsed()),
                    embeddingClient.embed(chunk.text()),
                    chunk.chunkType(),
                    chunk.sectionPath(),
                    chunk.headingTrail(),
                    chunk.tableId(),
                    chunk.slideId(),
                    chunk.parserConfidence()
                ))
                .toList(),
            MaterialIndexingStatus.READY,
            null,
            null,
            timestamp
        );
        return record;
    }

    private static final class RecordingShadowComparisonService implements LexicalShadowComparisonService {

        private int calls;
        private String providerType;
        private int productionMatchCount;

        @Override
        public void compareIfEligible(
            String query,
            com.example.demo.infrastructure.material.LexicalProviderType productionProviderType,
            List<com.example.demo.infrastructure.material.MaterialChunkSearchMatch> productionMatches,
            int limit
        ) {
            calls++;
            providerType = productionProviderType.propertyValue();
            productionMatchCount = productionMatches.size();
        }
    }
}
