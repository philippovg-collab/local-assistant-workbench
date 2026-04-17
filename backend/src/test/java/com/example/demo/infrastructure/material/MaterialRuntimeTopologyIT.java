package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    private MaterialSearchRepository searchRepository;

    @Autowired
    private MaterialIndexingQueueRepository indexingQueueRepository;

    @Autowired
    private ObjectProvider<LegacyMaterialImporter> legacyMaterialImporterProvider;

    @Autowired
    private ObjectProvider<FileMaterialRepository> fileMaterialRepositoryProvider;

    @Test
    void resolvesAllRuntimeMaterialInterfacesToSinglePostgresRepository() {
        assertInstanceOf(PostgresMaterialRepository.class, catalogRepository);
        assertSame(catalogRepository, searchRepository);
        assertSame(catalogRepository, indexingQueueRepository);
        assertEquals(PostgresMaterialRepository.class, catalogRepository.getClass());
    }

    @Test
    void excludesLegacyRuntimePathWhenLegacyImportIsDisabled() {
        assertNull(legacyMaterialImporterProvider.getIfAvailable());
        assertNull(fileMaterialRepositoryProvider.getIfAvailable());
    }
}
