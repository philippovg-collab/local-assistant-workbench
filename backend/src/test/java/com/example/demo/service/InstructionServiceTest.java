package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.instruction.FileInstructionRepository;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionDetail;
import com.example.demo.support.InMemoryInstructionRepository;
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

    @TempDir
    Path tempDir;

    @Test
    void findInstructionsByIdsPreservesRequestOrder() {
        InstructionService service = createService();
        InstructionDetail first = service.createInstruction(new CreateInstructionRequest(
            "Base role",
            "system",
            "Отвечай кратко."
        ));
        InstructionDetail second = service.createInstruction(new CreateInstructionRequest(
            "Safety",
            "safety",
            "Не раскрывай секреты."
        ));

        List<InstructionDetail> selected = service.findInstructionsByIds(List.of(second.id(), first.id()));

        assertEquals(List.of(second.id(), first.id()), selected.stream().map(InstructionDetail::id).toList());
    }

    @Test
    void concurrentCreatesProduceUniqueIds() throws Exception {
        InstructionService service = createService();
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<Future<InstructionDetail>> futures = IntStream.range(0, 20)
                .mapToObj(index -> executor.submit(() -> service.createInstruction(new CreateInstructionRequest(
                    "Instruction " + index,
                    "system",
                    "Content " + index
                ))))
                .toList();

            Set<String> ids = new HashSet<>();
            for (Future<InstructionDetail> future : futures) {
                ids.add(future.get().id());
            }

            assertEquals(20, ids.size());
            assertEquals(20, service.listInstructions().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ignoresBrokenInstructionFilesWithoutMutatingTheBucket() throws Exception {
        Files.createDirectories(tempDir.resolve("instructions"));
        Files.writeString(tempDir.resolve("instructions/broken.json"), "{not-json");

        InstructionService service = new InstructionService(new FileInstructionRepository(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(), tempDir.toString()));

        assertTrue(service.listInstructions().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("instructions").resolve("broken.json")));
    }

    @Test
    void throwsWhenRequestedInstructionIsMissing() {
        InstructionService service = createService();

        ApiException exception = assertThrows(ApiException.class, () -> service.findInstructionsByIds(List.of(
            "4cfde80e-bbbb-4f54-9a75-947bf4a0d145"
        )));
        assertEquals("instruction.not_found", exception.getCode());
    }

    @Test
    void updatesExistingInstructionWithoutChangingId() {
        InstructionService service = createService();
        InstructionDetail created = service.createInstruction(new CreateInstructionRequest(
            "Base role",
            "system",
            "Отвечай кратко."
        ));

        InstructionDetail updated = service.updateInstruction(created.id(), new CreateInstructionRequest(
            "Updated role",
            "context",
            "Отвечай развёрнуто."
        ));

        assertEquals(created.id(), updated.id());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals("Updated role", updated.title());
        assertEquals("Отвечай развёрнуто.", updated.content());
    }

    private InstructionService createService() {
        return new InstructionService(new InMemoryInstructionRepository());
    }
}
