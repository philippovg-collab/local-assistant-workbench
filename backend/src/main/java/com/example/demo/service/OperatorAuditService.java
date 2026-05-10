package com.example.demo.service;

import com.example.demo.service.audit.OperatorAuditEvent;
import com.example.demo.service.audit.port.OperatorAuditRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class OperatorAuditService {

    private static final Logger logger = LoggerFactory.getLogger(OperatorAuditService.class);

    private final OperatorAuditRepository repository;
    private final AuditRedactionService redactionService;

    public OperatorAuditService(
        OperatorAuditRepository repository,
        AuditRedactionService redactionService
    ) {
        this.repository = repository;
        this.redactionService = redactionService;
    }

    public OperatorAuditEvent record(OperatorAuditEvent event) {
        OperatorAuditEvent safeEvent = sanitize(event);
        String previousAuditEventId = MDC.get("auditEventId");
        try {
            MDC.put("auditEventId", safeEvent.id().toString());
            putEntityMdc(safeEvent);
            repository.save(safeEvent);
            logger.info(
                "Operator audit event eventType={} outcome={} entityType={} entityId={} requestId={}",
                safeEvent.eventType(),
                safeEvent.outcome(),
                safeEvent.entityType(),
                safeEvent.entityId(),
                safeEvent.requestId()
            );
        } catch (RuntimeException exception) {
            logger.warn(
                "Unable to persist operator audit event eventType={} requestId={}",
                safeEvent.eventType(),
                safeEvent.requestId(),
                exception
            );
        } finally {
            if (previousAuditEventId == null) {
                MDC.remove("auditEventId");
            } else {
                MDC.put("auditEventId", previousAuditEventId);
            }
            clearEntityMdc(safeEvent);
        }
        return safeEvent;
    }

    public int deleteEventsOlderThan(Instant cutoff) {
        return repository.deleteEventsOlderThan(cutoff);
    }

    private OperatorAuditEvent sanitize(OperatorAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Operator audit event must not be null");
        }
        Map<String, Object> metadata = redactionService.redactMetadata(event.metadata());
        return new OperatorAuditEvent(
            event.id(),
            event.occurredAt(),
            redactionService.redactStoredText(event.actor()),
            redactionService.redactStoredText(event.eventType()),
            redactionService.redactStoredText(event.outcome()),
            redactionService.redactStoredText(event.requestId()),
            redactionService.redactStoredText(event.method()),
            redactionService.redactStoredText(event.path()),
            redactionService.redactStoredText(event.entityType()),
            redactionService.redactStoredText(event.entityId()),
            redactionService.redactStoredText(event.workspaceKey()),
            event.statusCode(),
            redactionService.redactStoredText(event.failureCode()),
            redactionService.redactStoredText(event.failureMessage()),
            redactionService.redactStoredText(event.remoteAddr()),
            redactionService.redactStoredText(event.userAgent()),
            metadata
        );
    }

    private void putEntityMdc(OperatorAuditEvent event) {
        if (event.entityId() == null) {
            return;
        }
        if ("chat_run".equals(event.entityType())) {
            MDC.put("runId", event.entityId());
        } else if ("material".equals(event.entityType())) {
            MDC.put("materialId", event.entityId());
        }
    }

    private void clearEntityMdc(OperatorAuditEvent event) {
        if ("chat_run".equals(event.entityType())) {
            MDC.remove("runId");
        } else if ("material".equals(event.entityType())) {
            MDC.remove("materialId");
        }
    }
}
