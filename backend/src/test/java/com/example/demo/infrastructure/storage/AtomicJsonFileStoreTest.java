package com.example.demo.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicJsonFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsPathLikeIdsDuringReads() {
        AtomicJsonFileStore<TestRecord> store = createStore();

        ApiException exception = assertThrows(ApiException.class, () -> store.readById("../outside"));

        assertEquals("instructions.invalid_id", exception.getCode());
    }

    @Test
    void rejectsPathLikeIdsDuringDeletes() {
        AtomicJsonFileStore<TestRecord> store = createStore();

        ApiException exception = assertThrows(ApiException.class, () -> store.delete("nested/record"));

        assertEquals("instructions.invalid_id", exception.getCode());
    }

    @Test
    void rejectsPathLikeIdsDuringWrites() {
        AtomicJsonFileStore<TestRecord> store = createStore();

        ApiException exception = assertThrows(ApiException.class, () -> store.write(
            new TestRecord("..\\escape", "blocked")
        ));

        assertEquals("instructions.invalid_id", exception.getCode());
    }

    private AtomicJsonFileStore<TestRecord> createStore() {
        return new AtomicJsonFileStore<>(
            new ObjectMapper(),
            TestRecord.class,
            TestRecord::id,
            tempDir,
            "instructions"
        );
    }

    private record TestRecord(String id, String value) {
    }
}
