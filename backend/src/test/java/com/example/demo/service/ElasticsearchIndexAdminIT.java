package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.SearchableChunkDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.ElasticsearchLexicalSearchProvider;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestOverrides.class)
class ElasticsearchIndexAdminIT extends PostgresIntegrationTestSupport {

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
        registry.add("app.search-sync.index-prefix", () -> "rag-chunks-admin-it");
        registry.add("app.search-sync.index-version", () -> "v1");
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Autowired
    private ElasticsearchIndexAdminService indexAdminService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchLexicalSearchProvider elasticsearchLexicalSearchProvider;

    @Autowired
    private SearchSyncProperties searchSyncProperties;

    @BeforeEach
    void resetState() throws Exception {
        if (elasticsearchClient.indices().exists(request -> request.index("rag-chunks-admin-it-*")).value()) {
            elasticsearchClient.indices().delete(delete -> delete.index("rag-chunks-admin-it-*"));
        }
    }

    @Test
    void prepareWriteIndexDoesNotMoveExistingReadAliasOnVersionChange() throws Exception {
        indexAdminService.prepareWriteIndex("v1");
        indexAdminService.prepareWriteIndex("v2");

        assertEquals(Set.of("rag-chunks-admin-it-v2"), aliasTargets(searchSyncProperties.writeAlias()));
        assertEquals(Set.of("rag-chunks-admin-it-v1"), aliasTargets(searchSyncProperties.readAlias()));
    }

    @Test
    void promoteReadAliasControlsProductionReadCutover() throws Exception {
        indexAdminService.prepareWriteIndex("v1");
        indexDocument(
            "rag-chunks-admin-it-v1",
            new SearchableChunkDocument(
                "legacy-doc",
                "legacy-doc",
                "material-v1",
                "pricing-v1",
                "Legacy tariff",
                "legacyrollout anchor phrase",
                1,
                "direct-text",
                false,
                "text",
                Instant.parse("2026-04-18T05:00:00Z")
            )
        );
        refreshIndex("rag-chunks-admin-it-v1");

        indexAdminService.prepareWriteIndex("v2");
        indexDocument(
            searchSyncProperties.writeAlias(),
            new SearchableChunkDocument(
                "next-doc",
                "next-doc",
                "material-v2",
                "pricing-v2",
                "Next tariff",
                "nextgenrollout anchor phrase",
                1,
                "direct-text",
                false,
                "text",
                Instant.parse("2026-04-18T05:05:00Z")
            )
        );
        refreshIndex("rag-chunks-admin-it-v2");

        List<MaterialChunkSearchMatch> beforePromoteOld = elasticsearchLexicalSearchProvider.search("legacyrollout", 5);
        List<MaterialChunkSearchMatch> beforePromoteNew = elasticsearchLexicalSearchProvider.search("nextgenrollout", 5);

        assertEquals(Set.of("rag-chunks-admin-it-v2"), aliasTargets(searchSyncProperties.writeAlias()));
        assertEquals(Set.of("rag-chunks-admin-it-v1"), aliasTargets(searchSyncProperties.readAlias()));
        assertEquals(List.of("material-v1"), beforePromoteOld.stream().map(MaterialChunkSearchMatch::materialId).distinct().toList());
        assertTrue(beforePromoteNew.isEmpty());

        indexAdminService.promoteReadAlias("v2");
        refreshIndex("rag-chunks-admin-it-v2");

        List<MaterialChunkSearchMatch> afterPromoteOld = elasticsearchLexicalSearchProvider.search("legacyrollout", 5);
        List<MaterialChunkSearchMatch> afterPromoteNew = elasticsearchLexicalSearchProvider.search("nextgenrollout", 5);

        assertEquals(Set.of("rag-chunks-admin-it-v2"), aliasTargets(searchSyncProperties.readAlias()));
        assertTrue(afterPromoteOld.isEmpty());
        assertEquals(List.of("material-v2"), afterPromoteNew.stream().map(MaterialChunkSearchMatch::materialId).distinct().toList());
    }

    private void indexDocument(String indexName, SearchableChunkDocument document) throws IOException {
        elasticsearchClient.index(index -> index
            .index(indexName)
            .id(document.documentId())
            .document(document)
        );
    }

    private void refreshIndex(String indexName) throws IOException {
        elasticsearchClient.indices().refresh(refresh -> refresh.index(indexName));
    }

    private Set<String> aliasTargets(String aliasName) throws IOException {
        if (!elasticsearchClient.indices().existsAlias(request -> request.name(aliasName)).value()) {
            return Set.of();
        }

        GetAliasResponse response = elasticsearchClient.indices()
            .getAlias(request -> request.name(aliasName));
        return response.result().keySet();
    }
}
