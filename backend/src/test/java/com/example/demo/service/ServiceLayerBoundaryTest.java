package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceLayerBoundaryTest {

    @Test
    void serviceLayerDoesNotImportInfrastructure() throws IOException {
        Path serviceRoot = Path.of("src/main/java/com/example/demo/service");
        List<String> violations;
        try (var files = Files.walk(serviceRoot)) {
            violations = files
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> forbiddenInfrastructureImports(path).stream())
                .sorted()
                .toList();
        }

        assertTrue(
            violations.isEmpty(),
            () -> "Service layer must depend on service-level ports/domain, not infrastructure adapters: " + violations
        );
    }

    private static List<String> forbiddenInfrastructureImports(Path path) {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
        return source.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("import com.example.demo.infrastructure."))
            .map(line -> path + ": " + line)
            .toList();
    }
}
