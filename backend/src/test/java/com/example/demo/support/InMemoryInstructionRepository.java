package com.example.demo.support;

import com.example.demo.infrastructure.instruction.InstructionRepository;
import com.example.demo.infrastructure.instruction.StoredInstructionRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryInstructionRepository implements InstructionRepository {

    private final Map<String, StoredInstructionRecord> recordsById = new LinkedHashMap<>();

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
    public synchronized void save(StoredInstructionRecord record) {
        recordsById.put(record.id(), record);
    }

    @Override
    public synchronized void delete(String id) {
        recordsById.remove(id);
    }
}
