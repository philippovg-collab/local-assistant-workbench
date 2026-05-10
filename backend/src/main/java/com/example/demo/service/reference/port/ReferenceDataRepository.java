package com.example.demo.service.reference.port;

import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.service.reference.StoredReferenceWorkspaceRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReferenceDataRepository {

    List<StoredReferenceWorkspaceRecord> findWorkspaces(boolean activeOnly);

    Optional<StoredReferenceWorkspaceRecord> findWorkspaceByKey(String key);

    boolean workspaceExists(String key);

    int countDefaultWorkspaces();

    void clearDefaultWorkspacesExcept(String key, Instant updatedAt);

    StoredReferenceWorkspaceRecord saveWorkspace(StoredReferenceWorkspaceRecord record);

    List<StoredReferenceProjectRecord> findProjects(boolean activeOnly, String workspaceKey);

    Optional<StoredReferenceProjectRecord> findProjectByKey(String key);

    StoredReferenceProjectRecord saveProject(StoredReferenceProjectRecord record);
}
