package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.audit.PostgresChatAuditRepository;
import com.example.demo.infrastructure.audit.PostgresChatRunTraceRepository;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatRunTraceDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatRunQueryService {

    private static final int DEFAULT_LIST_LIMIT = 20;

    private final PostgresChatRunTraceRepository traceRepository;
    private final PostgresChatAuditRepository legacyRepository;

    public ChatRunQueryService(
        PostgresChatRunTraceRepository traceRepository,
        PostgresChatAuditRepository legacyRepository
    ) {
        this.traceRepository = traceRepository;
        this.legacyRepository = legacyRepository;
    }

    public List<ChatAuditRunSummary> listRuns() {
        List<ChatAuditRunSummary> modernRuns = traceRepository.findRunSummaries(DEFAULT_LIST_LIMIT);
        if (!modernRuns.isEmpty()) {
            return modernRuns;
        }
        return legacyRepository.findAll(DEFAULT_LIST_LIMIT).stream()
            .map(record -> new ChatAuditRunSummary(
                record.id(),
                record.mode(),
                record.model(),
                record.answerMode(),
                clip(record.prompt(), 120),
                clip(record.answer(), 160),
                record.createdAt()
            ))
            .toList();
    }

    public ChatAuditRunDetail getRun(String id) {
        String runId = requireValidId(id);
        return traceRepository.findAuditRunDetail(runId)
            .or(() -> legacyRepository.findById(runId).map(record -> ChatAuditJson.read(record.auditJson())))
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_audit.not_found",
                "Chat audit run '" + id + "' does not exist"
            ));
    }

    public ChatRunTraceDetail getTrace(String id) {
        String runId = requireValidId(id);
        return traceRepository.findTrace(runId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_trace.not_found",
                "Chat run trace '" + id + "' does not exist"
            ));
    }

    private String requireValidId(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat_audit.invalid_id",
                "Chat audit id must be a valid UUID",
                exception
            );
        }
    }

    private String clip(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
