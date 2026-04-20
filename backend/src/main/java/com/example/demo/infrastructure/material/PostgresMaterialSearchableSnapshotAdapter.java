package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialSearchableSnapshotAdapter
    extends PostgresMaterialJdbcSupport
    implements MaterialSearchableSnapshotRepository {

    PostgresMaterialSearchableSnapshotAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
