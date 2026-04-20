package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.MaterialCatalogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialCatalogAdapter extends PostgresMaterialJdbcSupport implements MaterialCatalogRepository {

    PostgresMaterialCatalogAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
