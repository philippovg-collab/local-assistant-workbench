package com.example.demo.infrastructure.storage;

import com.example.demo.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

public class AtomicJsonFileStore<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> type;
    private final Function<T, String> idResolver;
    private final Path dataDir;
    private final Path quarantineDir;
    private final String bucketName;

    public AtomicJsonFileStore(
        ObjectMapper objectMapper,
        Class<T> type,
        Function<T, String> idResolver,
        Path storageRoot,
        String bucketName
    ) {
        this.objectMapper = objectMapper;
        this.type = type;
        this.idResolver = idResolver;
        this.bucketName = bucketName;
        this.dataDir = storageRoot.resolve(bucketName);
        this.quarantineDir = storageRoot.resolve("quarantine").resolve(bucketName);

        try {
            Files.createDirectories(this.dataDir);
            Files.createDirectories(this.quarantineDir);
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                bucketName + ".storage_init_failed",
                "Unable to initialize storage bucket '" + bucketName + "'",
                exception
            );
        }
    }

    public List<T> readAll() {
        try (Stream<Path> stream = Files.list(dataDir)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted()
                .map(this::safeRead)
                .flatMap(Optional::stream)
                .toList();
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                bucketName + ".storage_read_failed",
                "Unable to read " + bucketName + " storage",
                exception
            );
        }
    }

    public Optional<T> readById(String id) {
        Path target = dataDir.resolve(id + ".json");
        if (!Files.exists(target)) {
            return Optional.empty();
        }

        return safeRead(target);
    }

    public void write(T record) {
        String id = idResolver.apply(record);
        Path target = dataDir.resolve(id + ".json");

        try {
            Path tempFile = Files.createTempFile(dataDir, id + "-", ".tmp");
            try {
                objectMapper.writeValue(tempFile.toFile(), record);
                moveAtomically(tempFile, target);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                bucketName + ".storage_write_failed",
                "Unable to write " + bucketName + " record",
                exception
            );
        }
    }

    public void delete(String id) {
        Path target = dataDir.resolve(id + ".json");
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                bucketName + ".storage_delete_failed",
                "Unable to delete " + bucketName + " record",
                exception
            );
        }
    }

    private Optional<T> safeRead(Path path) {
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), type));
        } catch (IOException exception) {
            quarantine(path);
            return Optional.empty();
        }
    }

    private void quarantine(Path path) {
        String baseName = path.getFileName().toString().replace(".json", "");
        String quarantinedName = baseName + "-broken-" + Instant.now().toEpochMilli() + ".json";
        Path quarantineTarget = quarantineDir.resolve(quarantinedName);

        try {
            moveAtomically(path, quarantineTarget);
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignoredAgain) {
                // Best effort cleanup. The file is already unreadable and should not break the whole bucket.
            }
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
