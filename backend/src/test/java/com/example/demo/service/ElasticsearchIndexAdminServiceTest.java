package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.SearchSyncProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ElasticsearchIndexAdminServiceTest {

    @Test
    void prepareConfiguredWriteIndexCreatesIndexAndInitialAliasesOnEmptyCluster() {
        FakeIndexManagementClient client = new FakeIndexManagementClient();
        ElasticsearchIndexAdminService service = new ElasticsearchIndexAdminService(client, properties());

        service.prepareConfiguredWriteIndex();

        assertEquals(Set.of("rag-chunks-unit-v1"), client.existingIndices);
        assertEquals(Set.of("rag-chunks-unit-v1"), client.aliasTargets.get("rag-chunks-unit-write"));
        assertEquals(Set.of("rag-chunks-unit-v1"), client.aliasTargets.get("rag-chunks-unit-read"));
        assertEquals(List.of("rag-chunks-unit-v1"), client.createdIndices);
        assertEquals(2, client.moveOperations.size());
    }

    @Test
    void prepareWriteIndexKeepsExistingReadAliasOnPreviousVersion() {
        FakeIndexManagementClient client = new FakeIndexManagementClient();
        client.existingIndices.add("rag-chunks-unit-v1");
        client.aliasTargets.put("rag-chunks-unit-read", new LinkedHashSet<>(Set.of("rag-chunks-unit-v1")));
        client.aliasTargets.put("rag-chunks-unit-write", new LinkedHashSet<>(Set.of("rag-chunks-unit-v1")));
        ElasticsearchIndexAdminService service = new ElasticsearchIndexAdminService(client, properties());

        service.prepareWriteIndex("v2");

        assertEquals(Set.of("rag-chunks-unit-v1", "rag-chunks-unit-v2"), client.existingIndices);
        assertEquals(Set.of("rag-chunks-unit-v1"), client.aliasTargets.get("rag-chunks-unit-read"));
        assertEquals(Set.of("rag-chunks-unit-v2"), client.aliasTargets.get("rag-chunks-unit-write"));
        assertEquals(List.of("rag-chunks-unit-v2"), client.createdIndices);
        assertEquals(1, client.moveOperations.size());
    }

    @Test
    void repeatedPrepareIsIdempotent() {
        FakeIndexManagementClient client = new FakeIndexManagementClient();
        ElasticsearchIndexAdminService service = new ElasticsearchIndexAdminService(client, properties());

        service.prepareConfiguredWriteIndex();
        service.prepareConfiguredWriteIndex();

        assertEquals(Set.of("rag-chunks-unit-v1"), client.existingIndices);
        assertEquals(Set.of("rag-chunks-unit-v1"), client.aliasTargets.get("rag-chunks-unit-write"));
        assertEquals(Set.of("rag-chunks-unit-v1"), client.aliasTargets.get("rag-chunks-unit-read"));
        assertEquals(List.of("rag-chunks-unit-v1"), client.createdIndices);
        assertEquals(3, client.moveOperations.size());
    }

    @Test
    void promoteReadAliasMovesPreparedVersionIntoReadPath() {
        FakeIndexManagementClient client = new FakeIndexManagementClient();
        client.existingIndices.add("rag-chunks-unit-v1");
        client.existingIndices.add("rag-chunks-unit-v2");
        client.aliasTargets.put("rag-chunks-unit-read", new LinkedHashSet<>(Set.of("rag-chunks-unit-v1")));
        ElasticsearchIndexAdminService service = new ElasticsearchIndexAdminService(client, properties());

        service.promoteReadAlias("v2");

        assertEquals(Set.of("rag-chunks-unit-v2"), client.aliasTargets.get("rag-chunks-unit-read"));
        assertTrue(client.moveOperations.size() >= 1);
    }

    @Test
    void resolvesCurrentWriteIndexFromAlias() {
        FakeIndexManagementClient client = new FakeIndexManagementClient();
        client.aliasTargets.put("rag-chunks-unit-write", new LinkedHashSet<>(Set.of("rag-chunks-unit-v2")));
        ElasticsearchIndexAdminService service = new ElasticsearchIndexAdminService(client, properties());

        assertEquals("rag-chunks-unit-v2", service.currentWriteIndex());
    }

    @Test
    void clearsCurrentWriteIndexWithoutMovingAliases() {
        FakeIndexManagementClient client = new FakeIndexManagementClient();
        client.aliasTargets.put("rag-chunks-unit-write", new LinkedHashSet<>(Set.of("rag-chunks-unit-v2")));
        client.deletedDocumentsByIndex.put("rag-chunks-unit-v2", 7L);
        ElasticsearchIndexAdminService service = new ElasticsearchIndexAdminService(client, properties());

        long deleted = service.clearCurrentWriteIndex();

        assertEquals(7L, deleted);
        assertEquals(List.of("rag-chunks-unit-v2"), client.deleteOperations);
        assertEquals(Set.of("rag-chunks-unit-v2"), client.aliasTargets.get("rag-chunks-unit-write"));
    }

    private SearchSyncProperties properties() {
        SearchSyncProperties properties = new SearchSyncProperties();
        properties.setEnabled(true);
        properties.setIndexPrefix("rag-chunks-unit");
        properties.setIndexVersion("v1");
        return properties;
    }

    private static final class FakeIndexManagementClient implements ElasticsearchIndexManagementClient {

        private final Set<String> existingIndices = new LinkedHashSet<>();
        private final Map<String, Set<String>> aliasTargets = new LinkedHashMap<>();
        private final Map<String, Long> deletedDocumentsByIndex = new LinkedHashMap<>();
        private final List<String> createdIndices = new ArrayList<>();
        private final List<String> moveOperations = new ArrayList<>();
        private final List<String> deleteOperations = new ArrayList<>();

        @Override
        public boolean indexExists(String indexName) {
            return existingIndices.contains(indexName);
        }

        @Override
        public void createIndex(String indexName, String indexDefinitionJson) {
            existingIndices.add(indexName);
            createdIndices.add(indexName);
        }

        @Override
        public Set<String> aliasTargets(String aliasName) {
            return new LinkedHashSet<>(aliasTargets.getOrDefault(aliasName, Set.of()));
        }

        @Override
        public void moveAlias(String aliasName, Set<String> currentTargets, String targetIndexName, boolean writeAlias) {
            aliasTargets.put(aliasName, new LinkedHashSet<>(Set.of(targetIndexName)));
            moveOperations.add(aliasName + "->" + targetIndexName + ":" + writeAlias);
        }

        @Override
        public long deleteAllDocuments(String indexName) {
            deleteOperations.add(indexName);
            return deletedDocumentsByIndex.getOrDefault(indexName, 0L);
        }
    }
}
