package com.example.demo.controller;

import com.example.demo.model.RagProjectRequest;
import com.example.demo.model.RagProjectSummary;
import com.example.demo.service.RagProjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag-projects")
public class RagProjectController {

    private final RagProjectService ragProjectService;

    public RagProjectController(RagProjectService ragProjectService) {
        this.ragProjectService = ragProjectService;
    }

    @GetMapping
    public List<RagProjectSummary> listProjects(
        @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return ragProjectService.listProjects(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RagProjectSummary createProject(@Valid @RequestBody RagProjectRequest request) {
        return ragProjectService.createProject(request);
    }

    @PutMapping("/{key}")
    public RagProjectSummary updateProject(
        @PathVariable String key,
        @Valid @RequestBody RagProjectRequest request
    ) {
        return ragProjectService.updateProject(key, request);
    }
}
