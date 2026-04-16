package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.instruction.FileInstructionRepository;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstructionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void findInstructionsByIdsPreservesRequestOrder() {
        InstructionService service = createService();
        InstructionSummary first = service.createInstruction(new CreateInstructionRequest(
            "Base role",
            "system",
            "Отвечай кратко."
        ));
        InstructionSummary second = service.createInstruction(new CreateInstructionRequest(
            "Safety",
            "safety",
            "Не раскрывай секреты."
        ));

        List<InstructionSummary> selected = service.findInstructionsByIds(List.of(second.id(), first.id()));

        assertEquals(List.of(second.id(), first.id()), selected.stream().map(InstructionSummary::id).toList());
    }

    @Test
    void concurrentCreatesProduceUniqueIds() throws Exception {
        InstructionService service = createService();
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<Future<InstructionSummary>> futures = IntStream.range(0, 20)
                .mapToObj(index -> executor.submit(() -> service.createInstruction(new CreateInstructionRequest(
                    "Instruction " + index,
                    "system",
                    "Content " + index
                ))))
                .toList();

            Set<String> ids = new HashSet<>();
            for (Future<InstructionSummary> future : futures) {
                ids.add(future.get().id());
            }

            assertEquals(20, ids.size());
            assertEquals(20, service.listInstructions().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void quarantinesBrokenInstructionFilesInsteadOfFailingTheWholeBucket() throws Exception {
        Files.createDirectories(tempDir.resolve("instructions"));
        Files.writeString(tempDir.resolve("instructions/broken.json"), "{not-json");

        InstructionService service = createService();

        assertTrue(service.listInstructions().isEmpty());
        try (var stream = Files.list(tempDir.resolve("quarantine").resolve("instructions"))) {
            assertTrue(stream.findAny().isPresent());
        }
    }

    @Test
    void throwsWhenRequestedInstructionIsMissing() {
        InstructionService service = createService();

        ApiException exception = assertThrows(ApiException.class, () -> service.findInstructionsByIds(List.of("missing")));
        assertEquals("instruction.not_found", exception.getCode());
    }

    private InstructionService createService() {
        return new InstructionService(new FileInstructionRepository(objectMapper, tempDir.toString()));
    }
}
