package com.example.demo.infrastructure.material;

import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.port.LexicalSearchProvider;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;

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
        assertInstanceOf(PostgresMaterialCatalogAdapter.class, catalogRepository);
        assertInstanceOf(PostgresMaterialLineageAdapter.class, lineageRepository);
        assertInstanceOf(PostgresMaterialChunkingAdapter.class, chunkingRepository);
        assertInstanceOf(PostgresMaterialSemanticSearchAdapter.class, semanticSearchRepository);
        assertInstanceOf(PostgresMaterialLexicalSearchAdapter.class, lexicalSearchProvider);
        assertInstanceOf(PostgresMaterialIndexingQueueAdapter.class, indexingQueueRepository);
        assertInstanceOf(PostgresMaterialSearchSyncQueueAdapter.class, searchSyncQueueRepository);
        assertInstanceOf(PostgresMaterialSearchableSnapshotAdapter.class, searchableSnapshotRepository);
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
