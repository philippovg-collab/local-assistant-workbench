package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.RagProjectRequest;
import com.example.demo.model.RagProjectSummary;
import com.example.demo.model.ReferenceWorkspaceRequest;
import com.example.demo.service.rag.StoredRagProjectSummary;
import com.example.demo.service.rag.port.RagProjectReadRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagProjectService {

    private final ReferenceDataService referenceDataService;
    private final RagProjectReadRepository readRepository;

    public RagProjectService(
        ReferenceDataService referenceDataService,
        RagProjectReadRepository readRepository
    ) {
        this.referenceDataService = referenceDataService;
        this.readRepository = readRepository;
    }

    public List<RagProjectSummary> listProjects(boolean activeOnly) {
        return readRepository.listSummariesWithCounts(activeOnly).stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public RagProjectSummary createProject(RagProjectRequest request) {
        var workspace = referenceDataService.createWorkspace(toWorkspaceRequest(request));
        return loadSavedSummary(workspace.key());
    }

    @Transactional
    public RagProjectSummary updateProject(String key, RagProjectRequest request) {
        var workspace = referenceDataService.updateWorkspace(key, toWorkspaceRequest(request));
        return loadSavedSummary(workspace.key());
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

    private RagProjectSummary loadSavedSummary(String key) {
        return readRepository.findSummaryByKey(key)
            .map(this::toSummary)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "rag_project.not_found",
                "RAG project '" + key + "' does not exist"
            ));
    }

    private RagProjectSummary toSummary(StoredRagProjectSummary project) {
        return new RagProjectSummary(
            project.key(),
            project.name(),
            project.description(),
            project.active(),
            project.isDefault(),
            project.sortOrder(),
            Math.toIntExact(project.materialCount()),
            Math.toIntExact(project.readyMaterialCount()),
            project.updatedAt()
        );
    }
}
