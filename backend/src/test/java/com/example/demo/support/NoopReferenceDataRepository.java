package com.example.demo.support;

import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.service.reference.StoredReferenceWorkspaceRecord;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class NoopReferenceDataRepository implements ReferenceDataRepository {

    @Override
    public List<StoredReferenceWorkspaceRecord> findWorkspaces(boolean activeOnly) {
        return List.of();
    }

    @Override
    public Optional<StoredReferenceWorkspaceRecord> findWorkspaceByKey(String key) {
        return Optional.empty();
    }

    @Override
    public boolean workspaceExists(String key) {
        return true;
    }

    @Override
    public int countDefaultWorkspaces() {
        return 0;
    }

    @Override
    public void clearDefaultWorkspacesExcept(String key, Instant updatedAt) {
    }

    @Override
    public StoredReferenceWorkspaceRecord saveWorkspace(StoredReferenceWorkspaceRecord record) {
        return record;
    }

    @Override
    public int countMaterialsByWorkspace(String workspaceKey) {
        return 0;
    }

    @Override
    public int countReadyMaterialsByWorkspace(String workspaceKey) {
        return 0;
    }

    @Override
    public List<StoredReferenceProjectRecord> findProjects(boolean activeOnly, String workspaceKey) {
        return List.of();
    }

    @Override
    public Optional<StoredReferenceProjectRecord> findProjectByKey(String key) {
        return Optional.empty();
    }

    @Override
    public boolean projectHasMaterialReferences(String key) {
        return false;
    }

    @Override
    public StoredReferenceProjectRecord saveProject(StoredReferenceProjectRecord record) {
        return record;
    }
}
