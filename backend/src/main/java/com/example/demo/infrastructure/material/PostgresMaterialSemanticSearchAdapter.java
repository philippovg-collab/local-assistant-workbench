package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.SemanticSearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialSemanticSearchAdapter
    extends PostgresMaterialJdbcSupport
    implements SemanticSearchRepository {

    PostgresMaterialSemanticSearchAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
