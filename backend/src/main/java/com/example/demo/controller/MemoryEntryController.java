package com.example.demo.controller;

import com.example.demo.model.MemoryEntryRequest;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.model.MemoryEntryUpdateRequest;
import com.example.demo.model.MemoryReviewActionRequest;
import com.example.demo.service.memory.MemoryEntryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory-entries")
public class MemoryEntryController {

    private final MemoryEntryService service;

    public MemoryEntryController(MemoryEntryService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemoryEntryResponse> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false, name = "type") String entryType,
        @RequestParam(required = false) String workspaceKey,
        @RequestParam(required = false) String projectKey
    ) {
        return service.list(
            StringUtils.hasText(status) ? MemoryEntryStatus.fromValue(status) : null,
            StringUtils.hasText(entryType) ? MemoryEntryType.fromValue(entryType) : null,
            workspaceKey,
            projectKey
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryEntryResponse create(@Valid @RequestBody MemoryEntryRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public MemoryEntryResponse update(
        @PathVariable String id,
        @Valid @RequestBody MemoryEntryUpdateRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/approve")
    public MemoryEntryResponse approve(
        @PathVariable String id,
        @Valid @RequestBody(required = false) MemoryReviewActionRequest request
    ) {
        return service.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public MemoryEntryResponse reject(
        @PathVariable String id,
        @Valid @RequestBody(required = false) MemoryReviewActionRequest request
    ) {
        return service.reject(id, request);
    }

    @PostMapping("/{id}/pin")
    public MemoryEntryResponse pin(
        @PathVariable String id,
        @Valid @RequestBody(required = false) MemoryReviewActionRequest request
    ) {
        return service.pin(id, request);
    }

    @PostMapping("/{id}/unpin")
    public MemoryEntryResponse unpin(
        @PathVariable String id,
        @Valid @RequestBody(required = false) MemoryReviewActionRequest request
    ) {
        return service.unpin(id, request);
    }

    @DeleteMapping("/{id}")
    public MemoryEntryResponse delete(
        @PathVariable String id,
        @Valid @RequestBody(required = false) MemoryReviewActionRequest request
    ) {
        return service.delete(id, request);
    }
}
