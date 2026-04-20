package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.QualityLayerMetricsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresQualityLayerMetricsAdapter extends PostgresMaterialJdbcSupport implements QualityLayerMetricsRepository {

    PostgresQualityLayerMetricsAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
