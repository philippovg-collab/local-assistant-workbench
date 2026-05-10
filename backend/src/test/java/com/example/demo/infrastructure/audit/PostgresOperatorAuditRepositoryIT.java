package com.example.demo.infrastructure.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.service.AuditRedactionService;
import com.example.demo.service.OperatorAuditService;
import com.example.demo.service.audit.OperatorAuditEvent;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresOperatorAuditRepositoryIT extends PostgresIntegrationTestSupport {

    private PostgresOperatorAuditRepository repository;
    private OperatorAuditService service;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        jdbcTemplate = database.jdbcTemplate();
        repository = new PostgresOperatorAuditRepository(jdbcTemplate, new ObjectMapper());
        service = new OperatorAuditService(
            repository,
            new AuditRedactionService(new ChatAuditProperties())
        );
    }

    @Test
    void servicePersistsRedactedAuditEventMetadataAsJsonb() {
        UUID id = UUID.randomUUID();

        service.record(new OperatorAuditEvent(
            id,
            Instant.parse("2026-05-10T00:00:00Z"),
            "admin",
            "material.update",
            "success",
            "req-1",
            "PUT",
            "/api/materials/material-1",
            "material",
            "material-1",
            "workspace-a",
            200,
            null,
            null,
            "127.0.0.1",
            "JUnit",
            Map.of(
                "password", "top-secret",
                "safe", "ok"
            )
        ));

        assertEquals("admin", stringValue("SELECT actor FROM operator_audit_events WHERE id = ?::uuid", id));
        assertEquals("material.update", stringValue("SELECT event_type FROM operator_audit_events WHERE id = ?::uuid", id));
        assertEquals("workspace-a", stringValue("SELECT workspace_key FROM operator_audit_events WHERE id = ?::uuid", id));
        String metadata = stringValue("SELECT metadata_jsonb::text FROM operator_audit_events WHERE id = ?::uuid", id);
        assertTrue(metadata.contains("[REDACTED]"));
        assertTrue(metadata.contains("ok"));
        assertFalse(metadata.contains("top-secret"));
    }

    @Test
    void deletesOnlyEventsOlderThanCutoff() {
        repository.save(event(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z")));
        repository.save(event(UUID.randomUUID(), Instant.parse("2026-02-01T00:00:00Z")));
        repository.save(event(UUID.randomUUID(), Instant.parse("2026-03-01T00:00:00Z")));

        int deleted = repository.deleteEventsOlderThan(Instant.parse("2026-02-15T00:00:00Z"));

        assertEquals(2, deleted);
        assertEquals(1, countRows());
        assertTrue(jdbcTemplate.queryForObject(
            "SELECT occurred_at::text FROM operator_audit_events",
            String.class
        ).startsWith("2026-03-01"));
    }

    private OperatorAuditEvent event(UUID id, Instant occurredAt) {
        return new OperatorAuditEvent(
            id,
            occurredAt,
            "admin",
            "test.event",
            "success",
            "req",
            "POST",
            "/api/test",
            "test",
            "entity",
            null,
            200,
            null,
            null,
            "127.0.0.1",
            "JUnit",
            Map.of()
        );
    }

    private String stringValue(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, String.class, id.toString());
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operator_audit_events", Integer.class);
        return count == null ? 0 : count;
    }
}
