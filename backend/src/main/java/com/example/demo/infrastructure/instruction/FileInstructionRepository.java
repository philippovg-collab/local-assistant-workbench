package com.example.demo.infrastructure.instruction;

import com.example.demo.infrastructure.storage.AtomicJsonFileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class FileInstructionRepository {

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
            "instructions"
        );
    }

    public List<StoredInstructionRecord> findAll() {
        return store.readAll();
    }

    public Optional<StoredInstructionRecord> findById(String id) {
        return store.readById(id);
    }

    public void save(StoredInstructionRecord record) {
        store.write(record);
    }

    public void delete(String id) {
        store.delete(id);
    }
}
