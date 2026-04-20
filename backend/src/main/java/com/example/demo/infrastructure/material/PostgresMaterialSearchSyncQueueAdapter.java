package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialSearchSyncQueueAdapter
    extends PostgresMaterialJdbcSupport
    implements MaterialSearchSyncQueueRepository {

    PostgresMaterialSearchSyncQueueAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
