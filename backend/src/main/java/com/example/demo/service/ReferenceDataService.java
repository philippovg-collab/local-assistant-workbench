package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import com.example.demo.service.reference.port.ReferenceUsageRepository;
import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.service.reference.StoredReferenceWorkspaceRecord;
import com.example.demo.model.ReferenceProject;
import com.example.demo.model.ReferenceProjectRequest;
import com.example.demo.model.ReferenceWorkspace;
import com.example.demo.model.ReferenceWorkspaceRequest;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReferenceDataService {

    private static final Pattern MACHINE_KEY_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final ReferenceDataRepository repository;
    private final ReferenceUsageRepository usageRepository;

    public ReferenceDataService(
        ReferenceDataRepository repository,
        ReferenceUsageRepository usageRepository
    ) {
        this.repository = repository;
        this.usageRepository = usageRepository;
    }

    public List<ReferenceWorkspace> listWorkspaces(boolean activeOnly) {
        return repository.findWorkspaces(activeOnly).stream().map(this::toWorkspace).toList();
    }

    @Transactional
    public ReferenceWorkspace createWorkspace(ReferenceWorkspaceRequest request) {
        String key = requireMachineKey(request == null ? null : request.key(), "reference_workspace.invalid_key");
        String nameRu = requireNameRu(request == null ? null : request.nameRu(), "reference_workspace.invalid_name");
        if (repository.findWorkspaceByKey(key).isPresent()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "reference_workspace.already_exists",
                "Reference workspace '" + key + "' already exists"
            );
        }

        boolean active = request == null || request.active() == null || request.active();
        boolean isDefault = request != null && Boolean.TRUE.equals(request.isDefault());
        validateDefaultWorkspaceState(active, isDefault);

        Instant now = Instant.now();
        if (isDefault) {
            repository.clearDefaultWorkspacesExcept(key, now);
        }
        return toWorkspace(repository.saveWorkspace(new StoredReferenceWorkspaceRecord(
            key,
            nameRu,
            sanitizeOptional(request == null ? null : request.description()),
            active,
            request == null || request.sortOrder() == null ? 0 : request.sortOrder(),
            isDefault,
            now,
            now
        )));
    }

    @Transactional
    public ReferenceWorkspace updateWorkspace(String pathKey, ReferenceWorkspaceRequest request) {
        String key = requireMachineKey(pathKey, "reference_workspace.invalid_key");
        requireMatchingBodyKey(key, request == null ? null : request.key(), "reference_workspace.key_mismatch");
        StoredReferenceWorkspaceRecord current = repository.findWorkspaceByKey(key)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "reference_workspace.not_found",
                "Reference workspace '" + key + "' does not exist"
            ));

        String nameRu = requireNameRu(request == null ? null : request.nameRu(), "reference_workspace.invalid_name");
        String description = request == null || request.description() == null
            ? current.description()
            : sanitizeOptional(request.description());
        boolean active = request == null || request.active() == null ? current.active() : request.active();
        boolean isDefault = request == null || request.isDefault() == null ? current.isDefault() : request.isDefault();
        int sortOrder = request == null || request.sortOrder() == null ? current.sortOrder() : request.sortOrder();

        if (current.isDefault() && (!active || !isDefault) && repository.countDefaultWorkspaces() <= 1) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "reference_workspace.default_required",
                "The only default reference workspace cannot be deactivated or unset as default"
            );
        }
        validateDefaultWorkspaceState(active, isDefault);

        Instant now = Instant.now();
        if (isDefault) {
            repository.clearDefaultWorkspacesExcept(key, now);
        }
        return toWorkspace(repository.saveWorkspace(new StoredReferenceWorkspaceRecord(
            key,
            nameRu,
            description,
            active,
            sortOrder,
            isDefault,
            current.createdAt(),
            now
        )));
    }

    public List<ReferenceProject> listProjects(boolean activeOnly, String workspaceKey) {
        String normalizedWorkspaceKey = normalizeOptionalMachineKey(
            workspaceKey,
            "reference_project.invalid_workspace_key"
        );
        return repository.findProjects(activeOnly, normalizedWorkspaceKey).stream().map(this::toProject).toList();
    }

    @Transactional
    public ReferenceProject createProject(ReferenceProjectRequest request) {
        String key = requireMachineKey(request == null ? null : request.key(), "reference_project.invalid_key");
        String workspaceKey = requireMachineKey(
            request == null ? null : request.workspaceKey(),
            "reference_project.invalid_workspace_key"
        );
        String nameRu = requireNameRu(request == null ? null : request.nameRu(), "reference_project.invalid_name");
        if (repository.findProjectByKey(key).isPresent()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "reference_project.already_exists",
                "Reference project '" + key + "' already exists"
            );
        }
        requireExistingWorkspace(workspaceKey);

        Instant now = Instant.now();
        return toProject(repository.saveProject(new StoredReferenceProjectRecord(
            key,
            workspaceKey,
            nameRu,
            request == null || request.active() == null || request.active(),
            request == null || request.sortOrder() == null ? 0 : request.sortOrder(),
            now,
            now
        )));
    }

    @Transactional
    public ReferenceProject updateProject(String pathKey, ReferenceProjectRequest request) {
        String key = requireMachineKey(pathKey, "reference_project.invalid_key");
        requireMatchingBodyKey(key, request == null ? null : request.key(), "reference_project.key_mismatch");
        StoredReferenceProjectRecord current = repository.findProjectByKey(key)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "reference_project.not_found",
                "Reference project '" + key + "' does not exist"
            ));

        String workspaceKey = requireMachineKey(
            request == null ? null : request.workspaceKey(),
            "reference_project.invalid_workspace_key"
        );
        requireExistingWorkspace(workspaceKey);
        String nameRu = requireNameRu(request == null ? null : request.nameRu(), "reference_project.invalid_name");
        if (!current.workspaceKey().equals(workspaceKey) && usageRepository.projectHasMaterialReferences(key)) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "reference_project.workspace_change_blocked_by_materials",
                "Reference project '" + key + "' cannot move to another workspace because it is used by materials"
            );
        }

        Instant now = Instant.now();
        return toProject(repository.saveProject(new StoredReferenceProjectRecord(
            key,
            workspaceKey,
            nameRu,
            request == null || request.active() == null ? current.active() : request.active(),
            request == null || request.sortOrder() == null ? current.sortOrder() : request.sortOrder(),
            current.createdAt(),
            now
        )));
    }

    private void requireExistingWorkspace(String workspaceKey) {
        if (!repository.workspaceExists(workspaceKey)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "reference_project.workspace_not_found",
                "Reference workspace '" + workspaceKey + "' does not exist"
            );
        }
    }

    private void validateDefaultWorkspaceState(boolean active, boolean isDefault) {
        if (isDefault && !active) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "reference_workspace.default_must_be_active",
                "Default reference workspace must be active"
            );
        }
    }

    private void requireMatchingBodyKey(String pathKey, String bodyKey, String code) {
        String normalizedBodyKey = normalizeOptionalMachineKey(bodyKey, code);
        if (normalizedBodyKey != null && !pathKey.equals(normalizedBodyKey)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                code,
                "Request body key must match path key"
            );
        }
    }

    private String requireMachineKey(String rawValue, String code) {
        String key = normalizeOptionalMachineKey(rawValue, code);
        if (key == null) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                code,
                "Reference key must be a lower-kebab-case machine key"
            );
        }
        return key;
    }

    private String normalizeOptionalMachineKey(String rawValue, String code) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String key = rawValue.trim();
        if (!MACHINE_KEY_PATTERN.matcher(key).matches()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                code,
                "Reference key must be a lower-kebab-case machine key"
            );
        }
        return key;
    }

    private String requireNameRu(String rawValue, String code) {
        if (!StringUtils.hasText(rawValue)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                code,
                "Field 'nameRu' must not be blank"
            );
        }
        return rawValue.trim();
    }

    private String sanitizeOptional(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim();
    }

    private ReferenceWorkspace toWorkspace(StoredReferenceWorkspaceRecord record) {
        return new ReferenceWorkspace(
            record.key(),
            record.nameRu(),
            record.description(),
            record.active(),
            record.sortOrder(),
            record.isDefault(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private ReferenceProject toProject(StoredReferenceProjectRecord record) {
        return new ReferenceProject(
            record.key(),
            record.workspaceKey(),
            record.nameRu(),
            record.active(),
            record.sortOrder(),
            record.createdAt(),
            record.updatedAt()
        );
    }
}
