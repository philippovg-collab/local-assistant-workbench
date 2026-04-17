package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

class RagStorageHealthServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RagStorageHealthService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        service = new RagStorageHealthService(jdbcTemplate);
    }

    @Test
    void reportsReadyWhenDatabaseAndPgvectorAreReachable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
            Integer.class
        )).thenReturn(1);

        RagStorageHealthService.StorageHealth health = service.currentHealth();

        assertTrue(health.ready());
        assertEquals("UP", health.databaseStatus());
        assertEquals("UP", health.vectorStatus());
    }

    @Test
    void reportsDatabaseAndVectorDownWhenPostgresIsUnreachable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
            .thenThrow(new DataAccessResourceFailureException("db offline"));

        RagStorageHealthService.StorageHealth health = service.currentHealth();

        assertFalse(health.ready());
        assertEquals("DOWN", health.databaseStatus());
        assertTrue(health.databaseReasonMessage().contains("db offline"));
        assertEquals("DOWN", health.vectorStatus());
    }

    @Test
    void reportsVectorDownWhenPgvectorExtensionIsMissing() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
            Integer.class
        )).thenReturn(0);

        RagStorageHealthService.StorageHealth health = service.currentHealth();

        assertFalse(health.ready());
        assertEquals("UP", health.databaseStatus());
        assertEquals("DOWN", health.vectorStatus());
        assertTrue(health.vectorReasonMessage().contains("not installed"));
    }

    @Test
    void reportsVectorDownWhenPgvectorReadinessCannotBeChecked() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
            Integer.class
        )).thenThrow(new BadSqlGrammarException(
            "query",
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
            new SQLException("permission denied")
        ));

        RagStorageHealthService.StorageHealth health = service.currentHealth();

        assertFalse(health.ready());
        assertEquals("UP", health.databaseStatus());
        assertEquals("DOWN", health.vectorStatus());
        assertTrue(health.vectorReasonMessage().contains("permission denied"));
    }
}
