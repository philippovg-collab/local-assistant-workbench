package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.LexicalSearchProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialLexicalSearchAdapter
    extends PostgresMaterialJdbcSupport
    implements LexicalSearchProvider {

    PostgresMaterialLexicalSearchAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
