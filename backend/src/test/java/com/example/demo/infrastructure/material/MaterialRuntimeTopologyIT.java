package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.demo.service.LexicalSearchStrategy;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(IntegrationTestOverrides.class)
class MaterialRuntimeTopologyIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MaterialCatalogRepository catalogRepository;

    @Autowired
    private SemanticSearchRepository semanticSearchRepository;

    @Autowired
    private LexicalSearchProvider lexicalSearchProvider;

    @Autowired
    private MaterialLineageRepository lineageRepository;

    @Autowired
    private MaterialChunkingRepository chunkingRepository;

    @Autowired
    private MaterialIndexingQueueRepository indexingQueueRepository;

    @Autowired
    private MaterialSearchSyncQueueRepository searchSyncQueueRepository;

    @Autowired
    private MaterialSearchableSnapshotRepository searchableSnapshotRepository;

    @Autowired
    private LexicalSearchStrategy lexicalSearchStrategy;

    @Autowired
    private ObjectProvider<PostgresMaterialRepository> legacyJdbcSupportProvider;

    @Autowired
    private ObjectProvider<LegacyMaterialImporter> legacyMaterialImporterProvider;

    @Autowired
    private ObjectProvider<FileMaterialRepository> fileMaterialRepositoryProvider;

    @Test
    void resolvesRuntimePortsToDedicatedPostgresAdapters() {
        assertInstanceOf(PostgresMaterialCatalogRepository.class, catalogRepository);
        assertInstanceOf(PostgresMaterialLineageRepository.class, lineageRepository);
        assertInstanceOf(PostgresMaterialChunkingRepository.class, chunkingRepository);
        assertInstanceOf(PostgresMaterialSemanticSearchRepository.class, semanticSearchRepository);
        assertInstanceOf(PostgresLexicalSearchProvider.class, lexicalSearchProvider);
        assertInstanceOf(PostgresMaterialIndexingQueueRepository.class, indexingQueueRepository);
        assertInstanceOf(PostgresMaterialSearchSyncQueueRepository.class, searchSyncQueueRepository);
        assertInstanceOf(PostgresMaterialSearchableSnapshotRepository.class, searchableSnapshotRepository);
        assertSame(lexicalSearchProvider, lexicalSearchStrategy.resolve());
        assertEquals(LexicalProviderType.POSTGRES, lexicalSearchStrategy.resolve().type());
        assertNotSame(catalogRepository, semanticSearchRepository);
        assertNotSame(catalogRepository, lexicalSearchProvider);
        assertNotSame(catalogRepository, indexingQueueRepository);
        assertNotSame(catalogRepository, searchSyncQueueRepository);
        assertNotSame(catalogRepository, searchableSnapshotRepository);
        assertNotSame(catalogRepository, lineageRepository);
        assertNotSame(catalogRepository, chunkingRepository);
        assertNull(legacyJdbcSupportProvider.getIfAvailable());
    }

    @Test
    void excludesLegacyRuntimePathWhenLegacyImportIsDisabled() {
        assertNull(legacyMaterialImporterProvider.getIfAvailable());
        assertNull(fileMaterialRepositoryProvider.getIfAvailable());
    }
}
