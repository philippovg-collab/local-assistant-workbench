package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.ChatAuditProperties;
import com.example.demo.infrastructure.audit.PostgresChatAuditRepository;
import com.example.demo.infrastructure.audit.StoredChatAuditRunRecord;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatAuditService {

    private static final int DEFAULT_LIST_LIMIT = 20;

    private final PostgresChatAuditRepository repository;
    private final ChatAuditProperties properties;
    private final ChatRunQueryService queryService;
    private final ChatRunTraceService traceService;
    private final AtomicInteger consecutiveFailureCount = new AtomicInteger(0);
    private final AtomicReference<AuditHealth> auditHealth = new AtomicReference<>(AuditHealth.up(null));

    public ChatAuditService(PostgresChatAuditRepository repository) {
        this(repository, new ChatAuditProperties(), null, null);
    }

    public ChatAuditService(PostgresChatAuditRepository repository, ChatAuditProperties properties) {
        this(repository, properties, null, null);
    }

    @Autowired
    public ChatAuditService(
        PostgresChatAuditRepository repository,
        ChatAuditProperties properties,
        ChatRunQueryService queryService,
        ChatRunTraceService traceService
    ) {
        this.repository = repository;
        this.properties = properties == null ? new ChatAuditProperties() : properties;
        this.queryService = queryService;
        this.traceService = traceService;
    }

    public String record(ChatExecutionResponse response) {
        if (response == null) {
            return null;
        }
        try {
            String auditRunId = doRecord(response);
            consecutiveFailureCount.set(0);
            auditHealth.set(AuditHealth.up(Instant.now()));
            return auditRunId;
        } catch (RuntimeException exception) {
            int failures = consecutiveFailureCount.incrementAndGet();
            if (failures >= Math.max(1, properties.getHealthFailureThreshold())) {
                auditHealth.set(AuditHealth.down(
                    reasonCode(exception),
                    rootMessage(exception),
                    failures,
                    Instant.now()
                ));
            }
            throw exception;
        }
    }

    private String doRecord(ChatExecutionResponse response) {
        ChatAuditRunDetail detail = new ChatAuditRunDetail(
            UUID.randomUUID().toString(),
            response.mode(),
            response.model(),
            response.prompt(),
            response.answer(),
            response.contextStatus(),
            response.answerModeApplied(),
            Instant.parse(response.createdAt()),
            response.instructionTrace(),
            response.knowledgeScopeResolved(),
            response.retrievalTrace(),
            response.sources()
        );
        repository.save(new StoredChatAuditRunRecord(
            detail.id(),
            detail.mode(),
            detail.model(),
            detail.prompt(),
            detail.answer(),
            detail.contextStatus(),
            detail.answerMode(),
            ChatAuditJson.write(detail),
            detail.createdAt()
        ));
        return detail.id();
    }

    public AuditHealth currentHealth() {
        if (traceService != null) {
            ChatRunTraceService.TraceHealth traceHealth = traceService.currentHealth();
            return new AuditHealth(
                traceHealth.status(),
                traceHealth.reasonCode(),
                traceHealth.reasonMessage(),
                traceHealth.consecutiveFailureCount(),
                traceHealth.lastStateChangedAt()
            );
        }
        return auditHealth.get();
    }

    public List<ChatAuditRunSummary> listRuns() {
        if (queryService != null) {
            return queryService.listRuns();
        }
        return repository.findAll(DEFAULT_LIST_LIMIT).stream()
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
        if (queryService != null) {
            return queryService.getRun(id);
        }
        return repository.findById(requireValidId(id))
            .map(record -> ChatAuditJson.read(record.auditJson()))
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_audit.not_found",
                "Chat audit run '" + id + "' does not exist"
            ));
    }

    public ChatRunTraceDetail getTrace(String id) {
        if (queryService == null) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_trace.not_found",
                "Chat run trace '" + id + "' does not exist"
            );
        }
        return queryService.getTrace(id);
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

    private String reasonCode(RuntimeException exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.getCode();
        }
        return "chat_audit.record_failed";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record AuditHealth(
        String status,
        String reasonCode,
        String reasonMessage,
        int consecutiveFailureCount,
        String lastStateChangedAt
    ) {
        private static AuditHealth up(Instant observedAt) {
            return new AuditHealth(
                "UP",
                null,
                null,
                0,
                observedAt == null ? null : observedAt.toString()
            );
        }

        private static AuditHealth down(
            String reasonCode,
            String reasonMessage,
            int consecutiveFailureCount,
            Instant observedAt
        ) {
            return new AuditHealth(
                "DOWN",
                reasonCode,
                reasonMessage,
                consecutiveFailureCount,
                observedAt == null ? null : observedAt.toString()
            );
        }
    }
}
