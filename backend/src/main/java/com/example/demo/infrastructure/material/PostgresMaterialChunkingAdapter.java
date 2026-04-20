package com.example.demo.infrastructure.material;

import com.example.demo.service.material.port.MaterialChunkingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialChunkingAdapter extends PostgresMaterialJdbcSupport implements MaterialChunkingRepository {

    PostgresMaterialChunkingAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }
}
