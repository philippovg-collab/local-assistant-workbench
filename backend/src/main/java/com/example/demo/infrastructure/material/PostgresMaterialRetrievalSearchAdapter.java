package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.LexicalSearchProvider;
import com.example.demo.service.material.port.SemanticSearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialRetrievalSearchAdapter
    extends PostgresMaterialJdbcSupport
    implements SemanticSearchRepository, LexicalSearchProvider {

    PostgresMaterialRetrievalSearchAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
