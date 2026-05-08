package com.example.demo.service;

import com.example.demo.service.audit.port.ChatAuditRepository;
import com.example.demo.service.audit.StoredChatAuditRunRecord;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatAuditService {

    private final ChatAuditRepository repository;
    private final ChatRunQueryService queryService;
    private final ChatRunTraceService traceService;
    private final AuditRedactionService redactionService;

    public ChatAuditService(
        ChatAuditRepository repository,
        ChatRunQueryService queryService,
        ChatRunTraceService traceService
    ) {
        this(repository, queryService, traceService, new AuditRedactionService(new com.example.demo.config.ChatAuditProperties()));
    }

    @Autowired
    public ChatAuditService(
        ChatAuditRepository repository,
        ChatRunQueryService queryService,
        ChatRunTraceService traceService,
        AuditRedactionService redactionService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
        this.redactionService = Objects.requireNonNull(redactionService, "redactionService");
    }

    public String record(ChatExecutionResponse response) {
        if (response == null) {
            return null;
        }
        return doRecord(response);
    }

    private String doRecord(ChatExecutionResponse response) {
        ChatAuditRunDetail detail = new ChatAuditRunDetail(
            UUID.randomUUID().toString(),
            response.mode(),
            response.model(),
            redactionService.redactStoredText(response.prompt()),
            redactionService.redactStoredText(response.answer()),
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
        ChatRunTraceService.TraceHealth traceHealth = traceService.currentHealth();
        return new AuditHealth(
            traceHealth.status(),
            traceHealth.reasonCode(),
            traceHealth.reasonMessage(),
            traceHealth.consecutiveFailureCount(),
            traceHealth.lastStateChangedAt()
        );
    }

    public List<ChatAuditRunSummary> listRuns() {
        return queryService.listRuns();
    }

    public ChatAuditRunDetail getRun(String id) {
        return queryService.getRun(id);
    }

    public ChatRunTraceDetail getTrace(String id) {
        return queryService.getTrace(id);
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
