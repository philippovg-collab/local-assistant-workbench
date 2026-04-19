package com.example.demo.service;

import com.example.demo.config.SearchSyncProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class ElasticsearchIndexAdminService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexAdminService.class);
    private static final String INDEX_DEFINITION_RESOURCE = "elasticsearch/searchable-chunks-index.json";

    private final ElasticsearchIndexManagementClient indexManagementClient;
    private final SearchSyncProperties searchSyncProperties;
    private final String indexDefinitionJson;

    public ElasticsearchIndexAdminService(
        ElasticsearchIndexManagementClient indexManagementClient,
        SearchSyncProperties searchSyncProperties
    ) {
        this.indexManagementClient = indexManagementClient;
        this.searchSyncProperties = searchSyncProperties;
        this.indexDefinitionJson = loadIndexDefinitionJson();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onApplicationReady() {
        try {
            prepareConfiguredWriteIndex();
        } catch (RuntimeException exception) {
            logger.warn(
                "Elasticsearch startup index preparation failed for index={} writeAlias={} readAlias={}: {}",
                searchSyncProperties.indexName(),
                searchSyncProperties.writeAlias(),
                searchSyncProperties.readAlias(),
                rootMessage(exception),
                exception
            );
        }
    }

    public void prepareConfiguredWriteIndex() {
        prepareWriteIndex(searchSyncProperties.getIndexVersion());
    }

    public void prepareWriteIndex(String indexVersion) {
        String targetIndexName = indexName(indexVersion);
        ensureIndexExists(targetIndexName);
        moveAlias(searchSyncProperties.writeAlias(), targetIndexName, true);
        createReadAliasIfMissing(targetIndexName);
    }

    public void promoteReadAlias(String indexVersion) {
        String targetIndexName = indexName(indexVersion);
        if (!indexManagementClient.indexExists(targetIndexName)) {
            throw new IllegalStateException(
                "Elasticsearch index '" + targetIndexName + "' does not exist and cannot be promoted"
            );
        }
        moveAlias(searchSyncProperties.readAlias(), targetIndexName, false);
    }

    public String currentWriteIndex() {
        Set<String> targets = indexManagementClient.aliasTargets(searchSyncProperties.writeAlias());
        if (targets.isEmpty()) {
            throw new IllegalStateException(
                "Elasticsearch write alias '" + searchSyncProperties.writeAlias() + "' is not configured"
            );
        }
        if (targets.size() != 1) {
            throw new IllegalStateException(
                "Elasticsearch write alias '" + searchSyncProperties.writeAlias() + "' must point to exactly one index"
            );
        }
        return targets.iterator().next();
    }

    public long clearCurrentWriteIndex() {
        return indexManagementClient.deleteAllDocuments(currentWriteIndex());
    }

    private String indexName(String indexVersion) {
        if (!StringUtils.hasText(indexVersion)) {
            throw new IllegalArgumentException("Elasticsearch index version must not be blank");
        }
        return searchSyncProperties.indexName(indexVersion.trim());
    }

    private void ensureIndexExists(String indexName) {
        if (!indexManagementClient.indexExists(indexName)) {
            indexManagementClient.createIndex(indexName, indexDefinitionJson);
        }
    }

    private void createReadAliasIfMissing(String targetIndexName) {
        if (indexManagementClient.aliasTargets(searchSyncProperties.readAlias()).isEmpty()) {
            moveAlias(searchSyncProperties.readAlias(), targetIndexName, false);
        }
    }

    private void moveAlias(String aliasName, String targetIndexName, boolean writeAlias) {
        indexManagementClient.moveAlias(
            aliasName,
            indexManagementClient.aliasTargets(aliasName),
            targetIndexName,
            writeAlias
        );
    }

    private String loadIndexDefinitionJson() {
        ClassPathResource resource = new ClassPathResource(INDEX_DEFINITION_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to load Elasticsearch index definition resource '" + INDEX_DEFINITION_RESOURCE + "'",
                exception
            );
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
