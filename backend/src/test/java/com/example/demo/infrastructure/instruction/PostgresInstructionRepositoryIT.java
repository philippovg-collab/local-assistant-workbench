package com.example.demo.infrastructure.instruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.InstructionCategory;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgresInstructionRepositoryIT extends PostgresIntegrationTestSupport {

    private PostgresInstructionRepository repository;

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        repository = new PostgresInstructionRepository(database.jdbcTemplate());
    }

    @Test
    void persistsUpdatesAndDeletesInstructionsViaOnConflict() {
        String id = UUID.randomUUID().toString();
        StoredInstructionRecord original = new StoredInstructionRecord(
            id,
            "Stay concise",
            InstructionCategory.SYSTEM,
            "Answer briefly.",
            "Answer briefly.",
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T09:00:00Z")
        );
        StoredInstructionRecord updated = new StoredInstructionRecord(
            id,
            "Stay extra concise",
            InstructionCategory.SYSTEM,
            "Answer very briefly.",
            "Answer very briefly.",
            Instant.parse("2026-04-17T12:00:00Z"),
            Instant.parse("2026-04-17T12:00:00Z")
        );

        repository.save(original);
        repository.save(updated);

        StoredInstructionRecord reloaded = repository.findById(id).orElseThrow();
        assertEquals("Stay extra concise", reloaded.title());
        assertEquals("Answer very briefly.", reloaded.content());
        assertEquals(original.createdAt(), reloaded.createdAt());
        assertEquals(updated.updatedAt(), reloaded.updatedAt());

        repository.delete(id);

        assertTrue(repository.findById(id).isEmpty());
    }

    @Test
    void returnsNewestInstructionsFirst() {
        repository.save(new StoredInstructionRecord(
            UUID.randomUUID().toString(),
            "Older",
            InstructionCategory.SYSTEM,
            "Old content",
            "Old content",
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T09:00:00Z")
        ));
        repository.save(new StoredInstructionRecord(
            UUID.randomUUID().toString(),
            "Newer",
            InstructionCategory.SYSTEM,
            "New content",
            "New content",
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        ));

        List<StoredInstructionRecord> records = repository.findAll();

        assertEquals(2, records.size());
        assertEquals("Newer", records.getFirst().title());
        assertEquals("Older", records.get(1).title());
    }
}
