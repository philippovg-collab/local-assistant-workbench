package com.example.demo.service;

import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatRunTraceDetail;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ChatAuditService {

    private final ChatRunQueryService queryService;
    private final ChatRunTraceService traceService;

    public ChatAuditService(
        ChatRunQueryService queryService,
        ChatRunTraceService traceService
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
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
