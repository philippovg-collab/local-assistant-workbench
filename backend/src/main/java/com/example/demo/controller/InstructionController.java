package com.example.demo.controller;

import java.util.List;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionSummary;
import com.example.demo.service.InstructionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instructions")
public class InstructionController {

    private final InstructionService instructionService;

    public InstructionController(InstructionService instructionService) {
        this.instructionService = instructionService;
    }

    @GetMapping
    public List<InstructionSummary> listInstructions() {
        return instructionService.listInstructions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstructionSummary createInstruction(@RequestBody CreateInstructionRequest request) {
        return instructionService.createInstruction(request);
    }

    @DeleteMapping("/{id}")
    public void deleteInstruction(@PathVariable String id) {
        instructionService.deleteInstruction(id);
    }
}
