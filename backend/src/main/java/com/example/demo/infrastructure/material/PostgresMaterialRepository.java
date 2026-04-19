package com.example.demo.infrastructure.material;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Thin compatibility wrapper for tests and manual instantiation.
 * Production wiring uses package-private {@link PostgresMaterialJdbcSupport} directly.
 */
public class PostgresMaterialRepository extends PostgresMaterialJdbcSupport {

    public PostgresMaterialRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
