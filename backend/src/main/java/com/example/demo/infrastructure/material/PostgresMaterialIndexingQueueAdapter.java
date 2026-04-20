package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialIndexingQueueAdapter extends PostgresMaterialJdbcSupport implements MaterialIndexingQueueRepository {

    PostgresMaterialIndexingQueueAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
