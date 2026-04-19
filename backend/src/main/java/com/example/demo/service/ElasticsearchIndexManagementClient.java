package com.example.demo.service;

import java.util.Set;

public interface ElasticsearchIndexManagementClient {

    boolean indexExists(String indexName);

    void createIndex(String indexName, String indexDefinitionJson);

    Set<String> aliasTargets(String aliasName);

    void moveAlias(String aliasName, Set<String> currentTargets, String targetIndexName, boolean writeAlias);

    long deleteAllDocuments(String indexName);
}
