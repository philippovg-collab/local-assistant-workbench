package com.example.demo.infrastructure.instruction;

import com.example.demo.infrastructure.storage.AtomicJsonFileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
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
    public void save(StoredInstructionRecord record) {
        store.write(record);
    }

    @Override
    public void delete(String id) {
        store.delete(id);
    }
}
