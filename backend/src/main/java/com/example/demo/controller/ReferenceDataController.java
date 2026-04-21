package com.example.demo.controller;

import com.example.demo.model.ReferenceProject;
import com.example.demo.model.ReferenceProjectRequest;
import com.example.demo.model.ReferenceWorkspace;
import com.example.demo.model.ReferenceWorkspaceRequest;
import com.example.demo.service.ReferenceDataService;
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
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/workspaces")
    public List<ReferenceWorkspace> listWorkspaces(
        @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return referenceDataService.listWorkspaces(activeOnly);
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public ReferenceWorkspace createWorkspace(@Valid @RequestBody ReferenceWorkspaceRequest request) {
        return referenceDataService.createWorkspace(request);
    }

    @PutMapping("/workspaces/{key}")
    public ReferenceWorkspace updateWorkspace(
        @PathVariable String key,
        @Valid @RequestBody ReferenceWorkspaceRequest request
    ) {
        return referenceDataService.updateWorkspace(key, request);
    }

    @GetMapping("/projects")
    public List<ReferenceProject> listProjects(
        @RequestParam(defaultValue = "true") boolean activeOnly,
        @RequestParam(required = false) String workspaceKey
    ) {
        return referenceDataService.listProjects(activeOnly, workspaceKey);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ReferenceProject createProject(@Valid @RequestBody ReferenceProjectRequest request) {
        return referenceDataService.createProject(request);
    }

    @PutMapping("/projects/{key}")
    public ReferenceProject updateProject(
        @PathVariable String key,
        @Valid @RequestBody ReferenceProjectRequest request
    ) {
        return referenceDataService.updateProject(key, request);
    }
}
