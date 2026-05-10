package com.example.demo.service;

import com.example.demo.config.RequestContext;
import com.example.demo.model.ClientEventRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientEventService {

    private static final Logger logger = LoggerFactory.getLogger("frontend.client_events");

    private final AuditRedactionService redactionService;

    public ClientEventService(AuditRedactionService redactionService) {
        this.redactionService = redactionService;
    }

    public void record(ClientEventRequest event, HttpServletRequest request) {
        String previousClientEventType = MDC.get("clientEventType");
        String previousClientRequestId = MDC.get("clientRequestId");
        try {
            MDC.put(RequestContext.ACTOR_MDC_KEY, RequestContext.currentActor());
            putMdc("clientEventType", event.type());
            putMdc("clientRequestId", event.requestId());

            SanitizedClientEvent safeEvent = sanitize(event);
            logBySeverity(safeEvent);
        } finally {
            restoreMdc("clientEventType", previousClientEventType);
            restoreMdc("clientRequestId", previousClientRequestId);
        }
    }

    private SanitizedClientEvent sanitize(ClientEventRequest event) {
        return new SanitizedClientEvent(
            normalizeSeverity(event.severity()),
            redactionService.redactStoredText(event.type()),
            redactionService.redactStoredText(event.message()),
            redactionService.redactStoredText(event.stack()),
            redactionService.redactStoredText(event.componentStack()),
            redactionService.redactStoredText(event.path()),
            redactionService.redactStoredText(event.requestId()),
            redactionService.redactMetadata(event.metadata())
        );
    }

    private void logBySeverity(SanitizedClientEvent event) {
        String message = "Frontend client event severity={} type={} clientPath={} clientRequestId={} message={} stack={} componentStack={} metadata={}";
        Object[] args = {
            event.severity(),
            event.type(),
            event.path(),
            event.requestId(),
            event.message(),
            event.stack(),
            event.componentStack(),
            event.metadata()
        };
        if ("error".equals(event.severity()) || "fatal".equals(event.severity())) {
            logger.error(message, args);
        } else if ("warn".equals(event.severity())) {
            logger.warn(message, args);
        } else {
            logger.info(message, args);
        }
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return "error";
        }
        String normalized = severity.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "info", "warn", "error", "fatal" -> normalized;
            default -> "error";
        };
    }

    private void putMdc(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, redactionService.redactStoredText(value));
        }
    }

    private void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    private record SanitizedClientEvent(
        String severity,
        String type,
        String message,
        String stack,
        String componentStack,
        String path,
        String requestId,
        Map<String, Object> metadata
    ) {
    }
}
