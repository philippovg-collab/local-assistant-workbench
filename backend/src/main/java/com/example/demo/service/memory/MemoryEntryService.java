package com.example.demo.service.memory;

import com.example.demo.config.ContextProperties;
import com.example.demo.config.RequestContext;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.MemoryEntryAction;
import com.example.demo.model.MemoryEntryRequest;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.model.MemoryEntryUpdateRequest;
import com.example.demo.model.MemoryReviewActionRequest;
import com.example.demo.service.memory.port.MemoryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemoryEntryService {

    private final MemoryRepository repository;
    private final MemorySafetyPolicy safetyPolicy;
    private final ContextProperties contextProperties;

    public MemoryEntryService(
        ContextProperties contextProperties,
        MemoryRepository repository,
        MemorySafetyPolicy safetyPolicy
    ) {
        this.contextProperties = contextProperties;
        this.repository = repository;
        this.safetyPolicy = safetyPolicy;
    }

    public List<MemoryEntryResponse> list(
        MemoryEntryStatus status,
        MemoryEntryType type,
        String workspaceKey,
        String projectKey
    ) {
        requireMemoryEnabled();
        return repository.listEntries(new MemoryEntryQuery(status, type, trimToNull(workspaceKey), trimToNull(projectKey)));
    }

    public MemoryEntryResponse create(MemoryEntryRequest request) {
        requireMemoryEnabled();
        MemoryEntryDraft draft = safetyPolicy.manualDraft(
            request == null ? null : request.entryType(),
            request == null ? null : request.contentText(),
            request == null ? null : request.normalizedKey(),
            request == null ? null : request.workspaceKey(),
            request == null ? null : request.projectKey(),
            request != null && Boolean.TRUE.equals(request.pinned()),
            request == null ? null : request.confidence(),
            request == null ? null : request.provenance()
        );
        return repository.createEntry(draft, actor(), request == null ? null : request.reason(), Instant.now());
    }

    public MemoryEntryResponse update(String entryId, MemoryEntryUpdateRequest request) {
        requireMemoryEnabled();
        MemoryEntryResponse existing = requireEntry(entryId);
        MemoryEntryDraft draft = safetyPolicy.manualDraft(
            request.entryType() == null ? existing.entryType() : request.entryType(),
            request.contentText() == null ? existing.contentText() : request.contentText(),
            request.normalizedKey() == null ? existing.normalizedKey() : request.normalizedKey(),
            request.workspaceKey() == null ? existing.workspaceKey() : request.workspaceKey(),
            request.projectKey() == null ? existing.projectKey() : request.projectKey(),
            Boolean.TRUE.equals(existing.pinned()),
            request.confidence() == null ? existing.confidence() : request.confidence(),
            existing.provenance()
        );
        return repository.updateEntry(entryId, draft, actor(), request.reason(), Instant.now());
    }

    public MemoryEntryResponse approve(String entryId, MemoryReviewActionRequest request) {
        requireMemoryEnabled();
        return repository.changeStatus(
            entryId,
            MemoryEntryStatus.PENDING_REVIEW,
            MemoryEntryStatus.APPROVED,
            MemoryEntryAction.APPROVE,
            actor(),
            request == null ? null : request.reason(),
            Instant.now()
        );
    }

    public MemoryEntryResponse reject(String entryId, MemoryReviewActionRequest request) {
        requireMemoryEnabled();
        return repository.changeStatus(
            entryId,
            MemoryEntryStatus.PENDING_REVIEW,
            MemoryEntryStatus.REJECTED,
            MemoryEntryAction.REJECT,
            actor(),
            request == null ? null : request.reason(),
            Instant.now()
        );
    }

    public MemoryEntryResponse pin(String entryId, MemoryReviewActionRequest request) {
        requireMemoryEnabled();
        return repository.setPinned(entryId, true, actor(), request == null ? null : request.reason(), Instant.now());
    }

    public MemoryEntryResponse unpin(String entryId, MemoryReviewActionRequest request) {
        requireMemoryEnabled();
        return repository.setPinned(entryId, false, actor(), request == null ? null : request.reason(), Instant.now());
    }

    public MemoryEntryResponse delete(String entryId, MemoryReviewActionRequest request) {
        requireMemoryEnabled();
        return repository.softDelete(entryId, actor(), request == null ? null : request.reason(), Instant.now());
    }

    private void requireMemoryEnabled() {
        if (contextProperties == null || !contextProperties.isLongTermMemoryEnabled()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.disabled",
                "Long-term memory review is disabled"
            );
        }
    }

    private MemoryEntryResponse requireEntry(String entryId) {
        return repository.findEntry(entryId).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "memory.not_found",
            "Memory entry '" + entryId + "' does not exist"
        ));
    }

    private String actor() {
        return RequestContext.currentActor();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
