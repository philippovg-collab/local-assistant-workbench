package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.MaterialLineageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialLineageAdapter extends PostgresMaterialJdbcSupport implements MaterialLineageRepository {

    PostgresMaterialLineageAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
