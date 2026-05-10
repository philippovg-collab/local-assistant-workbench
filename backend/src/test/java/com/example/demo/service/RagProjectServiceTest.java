package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.model.RagProjectRequest;
import com.example.demo.model.ReferenceWorkspace;
import com.example.demo.model.ReferenceWorkspaceRequest;
import com.example.demo.service.rag.StoredRagProjectSummary;
import com.example.demo.service.rag.port.RagProjectReadRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagProjectServiceTest {

    private final ReferenceDataService referenceDataService = mock(ReferenceDataService.class);
    private final RagProjectReadRepository readRepository = mock(RagProjectReadRepository.class);
    private final RagProjectService service = new RagProjectService(referenceDataService, readRepository);

    @Test
    void listsProjectsFromBoundedReadModel() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(readRepository.listSummariesWithCounts(false)).thenReturn(List.of(new StoredRagProjectSummary(
            "north-rag",
            "Северный RAG",
            "Корпус северного проекта",
            true,
            false,
            10,
            12,
            8,
            now
        )));

        var projects = service.listProjects(false);

        assertEquals(1, projects.size());
        assertEquals("north-rag", projects.getFirst().key());
        assertEquals("Северный RAG", projects.getFirst().name());
        assertEquals(12, projects.getFirst().materialCount());
        assertEquals(8, projects.getFirst().readyMaterialCount());
        verify(readRepository).listSummariesWithCounts(false);
        verifyNoInteractions(referenceDataService);
    }

    @Test
    void createsThroughReferenceWorkspaceAndReloadsSummaryFromReadModel() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(referenceDataService.createWorkspace(any())).thenReturn(new ReferenceWorkspace(
            "north-rag",
            "Северный RAG",
            "Корпус северного проекта",
            true,
            10,
            false,
            now,
            now
        ));
        when(readRepository.findSummaryByKey("north-rag")).thenReturn(Optional.of(new StoredRagProjectSummary(
            "north-rag",
            "Северный RAG",
            "Корпус северного проекта",
            true,
            false,
            10,
            3,
            2,
            now
        )));
        ArgumentCaptor<ReferenceWorkspaceRequest> requestCaptor = ArgumentCaptor.forClass(ReferenceWorkspaceRequest.class);

        var created = service.createProject(new RagProjectRequest(
            "north-rag",
            "Северный RAG",
            "Корпус северного проекта",
            true,
            10,
            false
        ));

        assertEquals(3, created.materialCount());
        assertEquals(2, created.readyMaterialCount());
        verify(referenceDataService).createWorkspace(requestCaptor.capture());
        assertEquals("north-rag", requestCaptor.getValue().key());
        assertEquals("Северный RAG", requestCaptor.getValue().nameRu());
        verify(readRepository).findSummaryByKey("north-rag");
    }

    @Test
    void updatesThroughReferenceWorkspaceAndReloadsSummaryFromReadModel() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(referenceDataService.updateWorkspace(any(), any())).thenReturn(new ReferenceWorkspace(
            "north-rag",
            "Северный RAG",
            "Обновленное описание",
            true,
            15,
            true,
            now,
            now
        ));
        when(readRepository.findSummaryByKey("north-rag")).thenReturn(Optional.of(new StoredRagProjectSummary(
            "north-rag",
            "Северный RAG",
            "Обновленное описание",
            true,
            true,
            15,
            5,
            4,
            now
        )));

        var updated = service.updateProject("north-rag", new RagProjectRequest(
            null,
            "Северный RAG",
            "Обновленное описание",
            true,
            15,
            true
        ));

        assertEquals(true, updated.isDefault());
        assertEquals(5, updated.materialCount());
        assertEquals(4, updated.readyMaterialCount());
        verify(referenceDataService).updateWorkspace("north-rag", new ReferenceWorkspaceRequest(
            null,
            "Северный RAG",
            "Обновленное описание",
            true,
            15,
            true
        ));
        verify(readRepository).findSummaryByKey("north-rag");
    }
}
