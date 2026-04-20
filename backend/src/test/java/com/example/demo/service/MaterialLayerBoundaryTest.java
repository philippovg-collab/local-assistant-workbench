package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaterialLayerBoundaryTest {

    @Test
    void serviceLayerDoesNotImportMaterialInfrastructure() throws IOException {
        Path serviceRoot = Path.of("src/main/java/com/example/demo/service");
        List<String> violations;
        try (var files = Files.walk(serviceRoot)) {
            violations = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> fileContains(path, "com.example.demo.infrastructure.material"))
                .map(Path::toString)
                .sorted()
                .toList();
        }

        assertTrue(
            violations.isEmpty(),
            () -> "Service layer must depend on service.material ports/domain, not infrastructure.material: " + violations
        );
    }

    private static boolean fileContains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }
}
