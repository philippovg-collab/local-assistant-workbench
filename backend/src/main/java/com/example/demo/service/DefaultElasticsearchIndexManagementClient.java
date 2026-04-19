package com.example.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class DefaultElasticsearchIndexManagementClient implements ElasticsearchIndexManagementClient {

    private final ElasticsearchClient elasticsearchClient;

    public DefaultElasticsearchIndexManagementClient(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public boolean indexExists(String indexName) {
        try {
            return elasticsearchClient.indices()
                .exists(request -> request.index(indexName))
                .value();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to check Elasticsearch index existence", exception);
        }
    }

    @Override
    public void createIndex(String indexName, String indexDefinitionJson) {
        try {
            elasticsearchClient.indices().create(request -> request
                .index(indexName)
                .withJson(new StringReader(indexDefinitionJson))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create Elasticsearch index", exception);
        }
    }

    @Override
    public Set<String> aliasTargets(String aliasName) {
        try {
            boolean aliasExists = elasticsearchClient.indices()
                .existsAlias(request -> request.name(aliasName))
                .value();
            if (!aliasExists) {
                return Set.of();
            }

            GetAliasResponse response = elasticsearchClient.indices()
                .getAlias(request -> request.name(aliasName));
            return new LinkedHashSet<>(response.result().keySet());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to resolve Elasticsearch alias '" + aliasName + "'", exception);
        }
    }

    @Override
    public void moveAlias(String aliasName, Set<String> currentTargets, String targetIndexName, boolean writeAlias) {
        try {
            elasticsearchClient.indices().updateAliases(request -> {
                for (String currentTarget : currentTargets) {
                    request.actions(action -> action.remove(remove -> remove
                        .index(currentTarget)
                        .alias(aliasName)
                    ));
                }
                request.actions(action -> action.add(add -> {
                    add.index(targetIndexName);
                    add.alias(aliasName);
                    if (writeAlias) {
                        add.isWriteIndex(true);
                    }
                    return add;
                }));
                return request;
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to ensure Elasticsearch alias '" + aliasName + "'", exception);
        }
    }

    @Override
    public long deleteAllDocuments(String indexName) {
        try {
            return elasticsearchClient.deleteByQuery(delete -> delete
                .index(indexName)
                .query(query -> query.matchAll(matchAll -> matchAll))
            ).deleted();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to delete Elasticsearch documents from index '" + indexName + "'",
                exception
            );
        }
    }
}
