package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import com.example.demo.service.reference.port.ReferenceUsageRepository;
import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.service.reference.StoredReferenceWorkspaceRecord;
import com.example.demo.model.ReferenceProjectRequest;
import com.example.demo.model.ReferenceWorkspaceRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReferenceDataServiceTest {

    private final ReferenceDataRepository repository = mock(ReferenceDataRepository.class);
    private final ReferenceUsageRepository usageRepository = mock(ReferenceUsageRepository.class);
    private final ReferenceDataService service = new ReferenceDataService(repository, usageRepository);

    @Test
    void rejectsNonMachineWorkspaceKeys() {
        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createWorkspace(
            new ReferenceWorkspaceRequest("North Upgrade", "Северный проект", null, true, 0, false)
        ));

        assertEquals("reference_workspace.invalid_key", exception.getCode());
    }

    @Test
    void rejectsBlankWorkspaceNames() {
        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createWorkspace(
            new ReferenceWorkspaceRequest("north-upgrade", " ", null, true, 0, false)
        ));

        assertEquals("reference_workspace.invalid_name", exception.getCode());
    }

    @Test
    void rejectsProjectsWithoutExistingWorkspace() {
        when(repository.workspaceExists("missing-workspace")).thenReturn(false);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createProject(
            new ReferenceProjectRequest("project-a", "missing-workspace", "Проект A", true, 0)
        ));

        assertEquals("reference_project.workspace_not_found", exception.getCode());
    }

    @Test
    void rejectsDeactivationOfTheOnlyDefaultWorkspace() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(repository.findWorkspaceByKey("general")).thenReturn(Optional.of(new StoredReferenceWorkspaceRecord(
            "general",
            "Общая",
            null,
            true,
            0,
            true,
            now,
            now
        )));
        when(repository.countDefaultWorkspaces()).thenReturn(1);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.updateWorkspace(
            "general",
            new ReferenceWorkspaceRequest(null, "Общая", null, false, 0, true)
        ));

        assertEquals("reference_workspace.default_required", exception.getCode());
    }

    @Test
    void clearsOtherDefaultsWhenWorkspaceBecomesDefault() {
        when(repository.findWorkspaceByKey("north-upgrade")).thenReturn(Optional.empty());
        when(repository.saveWorkspace(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createWorkspace(new ReferenceWorkspaceRequest(
            "north-upgrade",
            "Северная модернизация",
            "Описание северной модернизации",
            true,
            10,
            true
        ));

        verify(repository).clearDefaultWorkspacesExcept(eq("north-upgrade"), any());
        verify(repository).saveWorkspace(any(StoredReferenceWorkspaceRecord.class));
    }

    @Test
    void createsProjectWhenWorkspaceExists() {
        when(repository.findProjectByKey("north-grid")).thenReturn(Optional.empty());
        when(repository.workspaceExists("north-upgrade")).thenReturn(true);
        when(repository.saveProject(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createProject(new ReferenceProjectRequest(
            "north-grid",
            "north-upgrade",
            "Северная сеть",
            true,
            20
        ));

        assertEquals("north-grid", created.key());
        assertEquals("north-upgrade", created.workspaceKey());
        verify(repository).saveProject(any(StoredReferenceProjectRecord.class));
    }

    @Test
    void blocksWorkspaceChangeForProjectUsedByMaterials() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(repository.findProjectByKey("north-grid")).thenReturn(Optional.of(new StoredReferenceProjectRecord(
            "north-grid",
            "north-upgrade",
            "Северная сеть",
            true,
            20,
            now,
            now
        )));
        when(repository.workspaceExists("general")).thenReturn(true);
        when(usageRepository.projectHasMaterialReferences("north-grid")).thenReturn(true);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.updateProject(
            "north-grid",
            new ReferenceProjectRequest("north-grid", "general", "Северная сеть", true, 20)
        ));

        assertEquals("reference_project.workspace_change_blocked_by_materials", exception.getCode());
    }

    @Test
    void allowsWorkspaceChangeForUnusedProject() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(repository.findProjectByKey("north-grid")).thenReturn(Optional.of(new StoredReferenceProjectRecord(
            "north-grid",
            "north-upgrade",
            "Северная сеть",
            true,
            20,
            now,
            now
        )));
        when(repository.workspaceExists("general")).thenReturn(true);
        when(usageRepository.projectHasMaterialReferences("north-grid")).thenReturn(false);
        when(repository.saveProject(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateProject(
            "north-grid",
            new ReferenceProjectRequest("north-grid", "general", "Северная сеть", true, 20)
        );

        assertEquals("general", updated.workspaceKey());
        verify(repository).saveProject(any(StoredReferenceProjectRecord.class));
    }

    @Test
    void allowsNonWorkspaceUpdatesForProjectUsedByMaterials() {
        Instant now = Instant.parse("2026-04-20T10:00:00Z");
        when(repository.findProjectByKey("north-grid")).thenReturn(Optional.of(new StoredReferenceProjectRecord(
            "north-grid",
            "north-upgrade",
            "Северная сеть",
            true,
            20,
            now,
            now
        )));
        when(repository.workspaceExists("north-upgrade")).thenReturn(true);
        when(repository.saveProject(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateProject(
            "north-grid",
            new ReferenceProjectRequest("north-grid", "north-upgrade", "Сеть Север", false, 30)
        );

        assertEquals("north-upgrade", updated.workspaceKey());
        assertEquals("Сеть Север", updated.nameRu());
        assertEquals(false, updated.active());
        assertEquals(30, updated.sortOrder());
        verify(repository).saveProject(any(StoredReferenceProjectRecord.class));
    }
}
