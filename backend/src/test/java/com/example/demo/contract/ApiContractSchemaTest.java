package com.example.demo.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiContractSchemaTest {

    private static final Path CONTRACT_ARTIFACT =
        Path.of("src/main/resources/api-contract/frontend-api-contract.json");
    private static final Path CONTROLLER_ROOT =
        Path.of("src/main/java/com/example/demo/controller");
    private static final Pattern MODEL_IMPORT =
        Pattern.compile("^\\s*import\\s+(com\\.example\\.demo\\.model(?:\\.[A-Za-z0-9_]+)+);\\s*$");

    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @Test
    void generatedSchemaMatchesCheckedInArtifact() throws Exception {
        JsonNode generated = objectMapper.valueToTree(
            new ApiContractSchemaGenerator().generate(ApiContractRegistry.rootTypes())
        );

        if (Boolean.getBoolean("api.contract.write")) {
            Files.createDirectories(CONTRACT_ARTIFACT.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CONTRACT_ARTIFACT.toFile(), generated);
            return;
        }

        assertTrue(
            Files.exists(CONTRACT_ARTIFACT),
            "Missing checked-in API contract artifact. Run mvn -Dtest=ApiContractSchemaTest "
                + "-Dapi.contract.write=true test from backend/"
        );
        JsonNode checkedIn = objectMapper.readTree(CONTRACT_ARTIFACT.toFile());
        assertEquals(checkedIn, generated, "API contract artifact drifted; regenerate frontend-api-contract.json");
    }

    @Test
    void controllerModelImportsAreRegisteredAsContractRoots() throws IOException {
        Set<String> controllerImports = controllerModelImports();
        Set<String> registeredRoots = ApiContractRegistry.rootTypeNames();
        Set<String> missing = new TreeSet<>(controllerImports);
        missing.removeAll(registeredRoots);

        assertEquals(Set.of(), missing, "Controller-exposed DTOs must be added to ApiContractRegistry");
    }

    @Test
    void llmProviderContractDoesNotExposeStorageSecrets() throws Exception {
        JsonNode generated = objectMapper.valueToTree(
            new ApiContractSchemaGenerator().generate(ApiContractRegistry.rootTypes())
        );
        String contractJson = objectMapper.writeValueAsString(generated);

        assertFalse(contractJson.contains("com.example.demo.llmprovider.LlmProviderConfig"));
        assertFalse(contractJson.contains("apiKeyCiphertext"));
        assertFalse(contractJson.contains("api_key_ciphertext"));
    }

    private Set<String> controllerModelImports() throws IOException {
        Set<String> imports = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(CONTROLLER_ROOT)) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(path)) {
                    Matcher matcher = MODEL_IMPORT.matcher(line);
                    if (matcher.matches()) {
                        imports.add(matcher.group(1));
                    }
                }
            }
        }
        return imports;
    }
}
