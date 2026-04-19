package com.example.demo.support;

import com.example.demo.infrastructure.instruction.InstructionRepository;
import com.example.demo.infrastructure.instruction.StoredInstructionRecord;
import com.example.demo.infrastructure.instruction.StoredInstructionRevisionRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryInstructionRepository implements InstructionRepository {

    private final Map<String, StoredInstructionRecord> recordsById = new LinkedHashMap<>();
    private final Map<String, List<StoredInstructionRevisionRecord>> revisionsByInstructionId = new LinkedHashMap<>();

    @Override
    public synchronized List<StoredInstructionRecord> findAll() {
        return recordsById.values().stream().toList();
    }

    @Override
    public synchronized Optional<StoredInstructionRecord> findById(String id) {
        return Optional.ofNullable(recordsById.get(id));
    }

    @Override
    public synchronized List<StoredInstructionRecord> findAllByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
            .map(recordsById::get)
            .filter(record -> record != null)
            .toList();
    }

    @Override
    public synchronized List<StoredInstructionRecord> findByScope(StoredInstructionRecordScope scope) {
        if (scope == null) {
            return findAll();
        }

        return recordsById.values().stream()
            .filter(record -> record.scopeLevel() == scope.scopeLevel())
            .filter(record -> java.util.Objects.equals(record.scopeTargetId(), scope.scopeTargetId()))
            .filter(record -> !scope.activeOnly() || record.active())
            .toList();
    }

    @Override
    public synchronized List<StoredInstructionRevisionRecord> findRevisions(String instructionId) {
        return List.copyOf(revisionsByInstructionId.getOrDefault(instructionId, List.of()));
    }

    @Override
    public synchronized Optional<StoredInstructionRevisionRecord> findRevision(String instructionId, int revision) {
        return revisionsByInstructionId.getOrDefault(instructionId, List.of()).stream()
            .filter(record -> record.revision() == revision)
            .findFirst();
    }

    @Override
    public synchronized void save(StoredInstructionRecord record) {
        recordsById.put(record.id(), record);
    }

    @Override
    public synchronized void appendRevision(StoredInstructionRevisionRecord record) {
        revisionsByInstructionId
            .computeIfAbsent(record.instructionId(), ignored -> new ArrayList<>())
            .add(record);
    }

    @Override
    public synchronized void delete(String id) {
        recordsById.remove(id);
        revisionsByInstructionId.remove(id);
    }
}
