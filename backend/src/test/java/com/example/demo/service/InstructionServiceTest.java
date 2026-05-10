package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionRevisionDiff;
import com.example.demo.model.InstructionScopeLevel;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.support.InMemoryInstructionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionServiceTest {

    @Test
    void resolvesWorkspaceInstructionsUsingExplicitInstructionWorkspaceKey() {
        InstructionService service = new InstructionService(new InMemoryInstructionRepository());
        service.createInstruction(new CreateInstructionRequest(
            "Sales workspace",
            "system",
            "Use sales vocabulary.",
            InstructionScopeLevel.WORKSPACE_PROJECT,
            "sales-workspace",
            true
        ));
        service.createInstruction(new CreateInstructionRequest(
            "Default workspace",
            "system",
            "Use default vocabulary.",
            InstructionScopeLevel.WORKSPACE_PROJECT,
            InstructionService.DEFAULT_WORKSPACE_TARGET,
            true
        ));

        InstructionService.ResolvedInstructionContext context = service.resolveRuntimeInstructions(new ChatExecutionRequest(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Explain pipeline",
            null,
            List.of(),
            null,
            new KnowledgeScope(List.of(), List.of(), List.of(), "sales-workspace", false),
            "sales-workspace",
            null,
            null,
            null
        ));

        assertEquals(1, context.instructions().size());
        assertEquals("Sales workspace", context.instructions().getFirst().title());
    }

    @Test
    void rejectsInactiveScenarioInstructionsAtRuntime() {
        InstructionService service = new InstructionService(new InMemoryInstructionRepository());
        String instructionId = service.createInstruction(new CreateInstructionRequest(
            "Dormant scenario",
            "system",
            "Do not use me.",
            InstructionScopeLevel.CHAT_SCENARIO,
            null,
            false
        )).id();

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.resolveRuntimeInstructions(
            new ChatExecutionRequest(
                ChatMode.DIRECT,
                "qwen2.5:7b",
                "Explain pipeline",
                null,
                List.of(),
                null,
                null,
                List.of(instructionId),
                null
            )
        ));

        assertEquals("instruction.inactive", exception.getCode());
    }

    @Test
    void rejectsOversizedInstructionContent() {
        InstructionService service = new InstructionService(new InMemoryInstructionRepository());

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createInstruction(
            new CreateInstructionRequest("Rule", "system", "x".repeat(20_001))
        ));

        assertEquals("request.field_too_large", exception.getCode());
    }

    @Test
    void computesRevisionDiffBetweenTwoInstructionRevisions() {
        InstructionService service = new InstructionService(new InMemoryInstructionRepository());
        String instructionId = service.createInstruction(new CreateInstructionRequest(
            "Base rule",
            "system",
            "Answer briefly.",
            InstructionScopeLevel.CHAT_SCENARIO,
            null,
            true
        )).id();

        service.updateInstruction(instructionId, new CreateInstructionRequest(
            "Base rule",
            "system",
            "Answer briefly with bullets.",
            InstructionScopeLevel.CHAT_SCENARIO,
            null,
            true
        ));

        InstructionRevisionDiff diff = service.diffRevisions(instructionId, 1, 2);

        assertEquals(1, diff.fromRevision());
        assertEquals(2, diff.toRevision());
        assertTrue(diff.changes().stream().anyMatch(change -> "content".equals(change.field())));
    }
}
