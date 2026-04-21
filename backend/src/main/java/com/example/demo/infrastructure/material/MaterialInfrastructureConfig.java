package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.LexicalSearchProvider;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;
import com.example.demo.service.material.port.QualityLayerMetricsRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MaterialInfrastructureConfig {

    @Bean
    MaterialCatalogRepository postgresMaterialCatalogRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialCatalogAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    MaterialLineageRepository postgresMaterialLineageRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialLineageAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    MaterialChunkingRepository postgresMaterialChunkingRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialChunkingAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    MaterialIndexingQueueRepository postgresMaterialIndexingQueueRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialIndexingQueueAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    SemanticSearchRepository postgresMaterialSemanticSearchRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialSemanticSearchAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    LexicalSearchProvider postgresLexicalSearchProvider(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialLexicalSearchAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    MaterialSearchSyncQueueRepository postgresMaterialSearchSyncQueueRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialSearchSyncQueueAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    MaterialSearchableSnapshotRepository postgresMaterialSearchableSnapshotRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialSearchableSnapshotAdapter(jdbcTemplate, transactionManager);
    }

    @Bean
    QualityLayerMetricsRepository postgresQualityLayerMetricsRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresQualityLayerMetricsAdapter(jdbcTemplate, transactionManager);
    }
}
