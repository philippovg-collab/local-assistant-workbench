package com.example.demo.infrastructure.material;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Thin compatibility wrapper for tests and manual instantiation.
 * Production wiring uses focused adapter beans; this wrapper is kept for tests
 * and manual integration probes that still need the full material surface.
 */
public class PostgresMaterialRepository extends PostgresMaterialJdbcSupport {

    public PostgresMaterialRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
