package com.example.demo.service;

import com.example.demo.service.reference.port.ReferenceDataRepository;
import com.example.demo.model.RagProjectRequest;
import com.example.demo.model.RagProjectSummary;
import com.example.demo.model.ReferenceWorkspace;
import com.example.demo.model.ReferenceWorkspaceRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagProjectService {

    private final ReferenceDataService referenceDataService;
    private final ReferenceDataRepository repository;

    public RagProjectService(
        ReferenceDataService referenceDataService,
        ReferenceDataRepository repository
    ) {
        this.referenceDataService = referenceDataService;
        this.repository = repository;
    }

    public List<RagProjectSummary> listProjects(boolean activeOnly) {
        return referenceDataService.listWorkspaces(activeOnly).stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public RagProjectSummary createProject(RagProjectRequest request) {
        return toSummary(referenceDataService.createWorkspace(toWorkspaceRequest(request)));
    }

    @Transactional
    public RagProjectSummary updateProject(String key, RagProjectRequest request) {
        return toSummary(referenceDataService.updateWorkspace(key, toWorkspaceRequest(request)));
    }

    private ReferenceWorkspaceRequest toWorkspaceRequest(RagProjectRequest request) {
        if (request == null) {
            return null;
        }
        return new ReferenceWorkspaceRequest(
            request.key(),
            request.name(),
            request.description(),
            request.active(),
            request.sortOrder(),
            request.isDefault()
        );
    }

    private RagProjectSummary toSummary(ReferenceWorkspace workspace) {
        return new RagProjectSummary(
            workspace.key(),
            workspace.nameRu(),
            workspace.description(),
            workspace.active(),
            workspace.isDefault(),
            workspace.sortOrder(),
            repository.countMaterialsByWorkspace(workspace.key()),
            repository.countReadyMaterialsByWorkspace(workspace.key()),
            workspace.updatedAt()
        );
    }
}
