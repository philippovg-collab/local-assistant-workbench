package com.example.demo.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RagStorageHealthService {

    private final JdbcTemplate jdbcTemplate;

    public RagStorageHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StorageHealth currentHealth() {
        String databaseStatus = "UP";
        String databaseReasonMessage = null;
        String vectorStatus = "UP";
        String vectorReasonMessage = null;

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (DataAccessException exception) {
            databaseStatus = "DOWN";
            databaseReasonMessage = "PostgreSQL connection is unavailable: " + rootMessage(exception);
            vectorStatus = "DOWN";
            vectorReasonMessage = "pgvector is unavailable because PostgreSQL is unreachable";
            return new StorageHealth(databaseStatus, databaseReasonMessage, vectorStatus, vectorReasonMessage);
        }

        try {
            Integer vectorExtensionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class
            );
            if (vectorExtensionCount == null || vectorExtensionCount == 0) {
                vectorStatus = "DOWN";
                vectorReasonMessage = "pgvector extension is not installed in the configured database";
            }
        } catch (DataAccessException exception) {
            vectorStatus = "DOWN";
            vectorReasonMessage = "Unable to verify pgvector readiness: " + rootMessage(exception);
        }

        return new StorageHealth(databaseStatus, databaseReasonMessage, vectorStatus, vectorReasonMessage);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record StorageHealth(
        String databaseStatus,
        String databaseReasonMessage,
        String vectorStatus,
        String vectorReasonMessage
    ) {
        public boolean ready() {
            return "UP".equals(databaseStatus) && "UP".equals(vectorStatus);
        }
    }
}
