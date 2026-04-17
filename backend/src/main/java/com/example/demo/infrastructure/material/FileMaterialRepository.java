package com.example.demo.infrastructure.material;

import com.example.demo.infrastructure.storage.AtomicJsonFileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;

public class FileMaterialRepository {

    private final AtomicJsonFileStore<StoredMaterialRecord> store;

    public FileMaterialRepository(
        ObjectMapper objectMapper,
        @Value("${app.storage-dir:./storage}") String storageDir
    ) {
        this.store = new AtomicJsonFileStore<>(
            objectMapper,
            StoredMaterialRecord.class,
            StoredMaterialRecord::id,
            Path.of(storageDir).toAbsolutePath().normalize(),
            "materials",
            "material"
        );
    }

    public List<StoredMaterialRecord> findAll() {
        return store.readAll();
    }

    public Optional<StoredMaterialRecord> findById(String id) {
        return store.readById(id);
    }

    public void save(StoredMaterialRecord record) {
        store.write(record);
    }

    public void delete(String id) {
        store.delete(id);
    }
}
