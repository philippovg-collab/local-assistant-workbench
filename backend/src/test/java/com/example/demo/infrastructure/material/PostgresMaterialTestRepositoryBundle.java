package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.LexicalSearchProvider;
import com.example.demo.service.material.port.MaterialAutoTaggingTaskRepository;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;
import com.example.demo.service.material.port.QualityLayerMetricsRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public final class PostgresMaterialTestRepositoryBundle {

    private final MaterialCatalogRepository catalog;
    private final MaterialLineageRepository lineage;
    private final MaterialChunkingRepository chunking;
    private final MaterialIndexingQueueRepository indexingQueue;
    private final MaterialAutoTaggingTaskRepository autoTaggingTasks;
    private final MaterialSearchSyncQueueRepository searchSyncQueue;
    private final MaterialSearchableSnapshotRepository searchableSnapshot;
    private final QualityLayerMetricsRepository qualityMetrics;
    private final SemanticSearchRepository semantic;
    private final LexicalSearchProvider lexical;

    public PostgresMaterialTestRepositoryBundle(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.catalog = new PostgresMaterialCatalogAdapter(jdbcTemplate, transactionManager);
        this.lineage = new PostgresMaterialLineageAdapter(jdbcTemplate, transactionManager);
        this.chunking = new PostgresMaterialChunkingAdapter(jdbcTemplate, transactionManager);
        this.indexingQueue = new PostgresMaterialIndexingQueueAdapter(jdbcTemplate, transactionManager);
        this.autoTaggingTasks = new PostgresMaterialAutoTaggingTaskAdapter(jdbcTemplate, transactionManager);
        this.searchSyncQueue = new PostgresMaterialSearchSyncQueueAdapter(jdbcTemplate, transactionManager);
        this.searchableSnapshot = new PostgresMaterialSearchableSnapshotAdapter(jdbcTemplate, transactionManager);
        this.qualityMetrics = new PostgresQualityLayerMetricsAdapter(jdbcTemplate, transactionManager);
        this.semantic = new PostgresMaterialSemanticSearchAdapter(jdbcTemplate, transactionManager);
        this.lexical = new PostgresMaterialLexicalSearchAdapter(jdbcTemplate, transactionManager);
    }

    public MaterialCatalogRepository catalog() {
        return catalog;
    }

    public MaterialLineageRepository lineage() {
        return lineage;
    }

    public MaterialChunkingRepository chunking() {
        return chunking;
    }

    public MaterialIndexingQueueRepository indexingQueue() {
        return indexingQueue;
    }

    public MaterialAutoTaggingTaskRepository autoTaggingTasks() {
        return autoTaggingTasks;
    }

    public MaterialSearchSyncQueueRepository searchSyncQueue() {
        return searchSyncQueue;
    }

    public MaterialSearchableSnapshotRepository searchableSnapshot() {
        return searchableSnapshot;
    }

    public QualityLayerMetricsRepository qualityMetrics() {
        return qualityMetrics;
    }

    public SemanticSearchRepository semantic() {
        return semantic;
    }

    public LexicalSearchProvider lexical() {
        return lexical;
    }
}
