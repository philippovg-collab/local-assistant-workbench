package com.example.demo.support;

import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialChunkingRepository;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.MaterialLineageRepository;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.infrastructure.material.SemanticSearchRepository;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.AnswerModePostProcessor;
import com.example.demo.service.ElasticsearchIndexSyncService;
import com.example.demo.service.HybridChunkRanker;
import com.example.demo.service.LexicalShadowComparisonService;
import com.example.demo.service.MaterialContentSupport;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.MaterialSearchSyncLifecycleService;
import com.example.demo.service.ProductionLexicalSearchRouter;

public final class TestMaterialServices {

    private TestMaterialServices() {
    }

    public static MaterialSearchSyncLifecycleService lifecycleService(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        MaterialChunkingRepository chunkingRepository,
        MaterialIndexingQueueRepository indexingQueueRepository,
        MaterialSearchSyncQueueRepository queueRepository
    ) {
        return new MaterialSearchSyncLifecycleService(
            catalogRepository,
            lineageRepository,
            chunkingRepository,
            indexingQueueRepository,
            queueRepository,
            new AfterCommitExecutor(),
            TestObjectProviders.<ElasticsearchIndexSyncService>empty()
        );
    }

    public static MaterialRetrievalService retrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository,
        SemanticSearchRepository semanticSearchRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport
    ) {
        return retrievalService(
            catalogRepository,
            chunkingRepository,
            semanticSearchRepository,
            productionLexicalSearchRouter,
            embeddingClient,
            ragProperties,
            hybridChunkRanker,
            contentSupport,
            (query, productionProvider, productionMatches, limit) -> {
            }
        );
    }

    public static MaterialRetrievalService retrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository,
        SemanticSearchRepository semanticSearchRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport,
        LexicalShadowComparisonService shadowComparisonService
    ) {
        return new MaterialRetrievalService(
            catalogRepository,
            chunkingRepository,
            semanticSearchRepository,
            productionLexicalSearchRouter,
            embeddingClient,
            ragProperties,
            hybridChunkRanker,
            contentSupport,
            shadowComparisonService,
            new AnswerModePostProcessor(contentSupport)
        );
    }
}
