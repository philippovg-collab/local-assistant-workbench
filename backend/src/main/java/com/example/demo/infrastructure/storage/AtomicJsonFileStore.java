package com.example.demo.infrastructure.storage;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class AtomicJsonFileStore<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> type;
    private final Function<T, String> idResolver;
    private final Path dataDir;
    private final String bucketName;
    private final String errorNamespace;

    public AtomicJsonFileStore(
        ObjectMapper objectMapper,
        Class<T> type,
        Function<T, String> idResolver,
        Path storageRoot,
        String bucketName
    ) {
        this(
            objectMapper,
            type,
            idResolver,
            storageRoot,
            bucketName,
            bucketName
        );
    }

    public AtomicJsonFileStore(
        ObjectMapper objectMapper,
        Class<T> type,
        Function<T, String> idResolver,
        Path storageRoot,
        String bucketName,
        String errorNamespace
    ) {
        this.objectMapper = objectMapper;
        this.type = type;
        this.idResolver = idResolver;
        this.bucketName = bucketName;
        this.errorNamespace = errorNamespace;
        this.dataDir = storageRoot.resolve(bucketName);

        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                errorNamespace + ".storage_init_failed",
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                errorNamespace + ".storage_read_failed",
                "Unable to read " + bucketName + " storage",
                exception
            );
        }
    }

    public Optional<T> readById(String id) {
        Path target = resolveRecordPath(id);
        if (!Files.exists(target)) {
            return Optional.empty();
        }

        return safeRead(target);
    }

    public void write(T record) {
        String id = validateId(idResolver.apply(record));
        Path target = resolveRecordPath(id);

        try {
            Path tempFile = Files.createTempFile(dataDir, tempFilePrefix(id), ".tmp");
            try {
                objectMapper.writeValue(tempFile.toFile(), record);
                moveAtomically(tempFile, target);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                errorNamespace + ".storage_write_failed",
                "Unable to write " + bucketName + " record",
                exception
            );
        }
    }

    public void delete(String id) {
        Path target = resolveRecordPath(id);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                errorNamespace + ".storage_delete_failed",
                "Unable to delete " + bucketName + " record",
                exception
            );
        }
    }

    private Optional<T> safeRead(Path path) {
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), type));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Path resolveRecordPath(String id) {
        String validatedId = validateId(id);

        try {
            Path target = dataDir.resolve(validatedId + ".json").normalize();
            if (!target.startsWith(dataDir)) {
                throw invalidId(validatedId);
            }
            return target;
        } catch (InvalidPathException exception) {
            throw invalidId(id, exception);
        }
    }

    private String validateId(String id) {
        if (id == null) {
            throw invalidId(null);
        }

        String trimmedId = id.trim();
        if (trimmedId.isEmpty() || trimmedId.contains("..") || trimmedId.contains("/") || trimmedId.contains("\\")) {
            throw invalidId(id);
        }

        return trimmedId;
    }

    private String tempFilePrefix(String id) {
        return id.length() >= 3 ? id + "-" : bucketName + "-tmp-";
    }

    private StorageException invalidId(String id) {
        return invalidId(id, null);
    }

    private StorageException invalidId(String id, Throwable cause) {
        return new StorageException(
            ErrorType.INVALID_REQUEST,
            errorNamespace + ".invalid_id",
            "Invalid " + bucketName + " id: " + id,
            cause
        );
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
