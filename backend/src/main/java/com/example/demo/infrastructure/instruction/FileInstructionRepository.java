package com.example.demo.infrastructure.instruction;

import com.example.demo.infrastructure.storage.AtomicJsonFileStore;
import com.example.demo.model.InstructionScopeLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
public class FileInstructionRepository implements InstructionRepository {

    private final AtomicJsonFileStore<StoredInstructionRecord> store;

    public FileInstructionRepository(
        ObjectMapper objectMapper,
        @Value("${app.storage-dir:./storage}") String storageDir
    ) {
        this.store = new AtomicJsonFileStore<>(
            objectMapper,
            StoredInstructionRecord.class,
            StoredInstructionRecord::id,
            Path.of(storageDir).toAbsolutePath().normalize(),
            "instructions",
            "instruction"
        );
    }

    @Override
    public List<StoredInstructionRecord> findAll() {
        return store.readAll();
    }

    @Override
    public Optional<StoredInstructionRecord> findById(String id) {
        return store.readById(id);
    }

    @Override
    public List<StoredInstructionRecord> findAllByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
            .map(store::readById)
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public List<StoredInstructionRecord> findByScope(StoredInstructionRecordScope scope) {
        if (scope == null || scope.scopeLevel() == null) {
            return List.of();
        }

        return findAll().stream()
            .filter(record -> record.scopeLevel() == scope.scopeLevel())
            .filter(record -> {
                if (scope.scopeTargetId() == null || scope.scopeTargetId().isBlank()) {
                    return record.scopeTargetId() == null;
                }
                return scope.scopeTargetId().equals(record.scopeTargetId());
            })
            .filter(record -> !scope.activeOnly() || record.active())
            .toList();
    }

    @Override
    public List<StoredInstructionRevisionRecord> findRevisions(String instructionId) {
        return findById(instructionId)
            .map(record -> List.of(new StoredInstructionRevisionRecord(
                record.id(),
                record.revision(),
                record.title(),
                record.category(),
                record.content(),
                record.normalizedContent(),
                record.scopeLevel() == null ? InstructionScopeLevel.CHAT_SCENARIO : record.scopeLevel(),
                record.scopeTargetId(),
                record.active(),
                null,
                record.createdAt(),
                record.updatedAt()
            )))
            .orElse(List.of());
    }

    @Override
    public Optional<StoredInstructionRevisionRecord> findRevision(String instructionId, int revision) {
        return findRevisions(instructionId).stream()
            .filter(record -> record.revision() == revision)
            .findFirst();
    }

    @Override
    public void save(StoredInstructionRecord record) {
        store.write(record);
    }

    @Override
    public void appendRevision(StoredInstructionRevisionRecord record) {
        // Legacy file-backed repository is used only for migration import.
    }

    @Override
    public void delete(String id) {
        store.delete(id);
    }
}
