package com.example.demo.api;

import com.example.demo.config.RequestContext;
import com.example.demo.service.OperatorAuditService;
import com.example.demo.service.audit.OperatorAuditEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class OperatorAuditFilter extends OncePerRequestFilter {

    private static final int MAX_RESPONSE_CAPTURE_BYTES = 65_536;
    private static final List<String> ENTITY_ID_RESPONSE_FIELDS = List.of(
        "id",
        "auditRunId",
        "materialId",
        "projectKey",
        "workspaceKey",
        "key"
    );

    private final ObjectProvider<OperatorAuditService> auditServiceProvider;
    private final ObjectMapper objectMapper;

    public OperatorAuditFilter(
        ObjectProvider<OperatorAuditService> auditServiceProvider,
        ObjectMapper objectMapper
    ) {
        this.auditServiceProvider = auditServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        AuditOperation operation = resolveOperation(request);
        OperatorAuditService auditService = auditServiceProvider.getIfAvailable();
        if (operation == null || auditService == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String actorBeforeChain = RequestContext.currentActor(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        Throwable failure = null;
        try {
            filterChain.doFilter(request, responseWrapper);
        } catch (ServletException | IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                recordAuditEvent(request, responseWrapper, operation, actorBeforeChain, failure, auditService);
            } finally {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    private void recordAuditEvent(
        HttpServletRequest request,
        ContentCachingResponseWrapper response,
        AuditOperation operation,
        String actorBeforeChain,
        Throwable failure,
        OperatorAuditService auditService
    ) {
        int status = response.getStatus();
        if (failure != null && status < 400) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        boolean success = status >= 200 && status < 400;
        JsonNode responseJson = readResponseJson(response);
        String entityId = firstNonBlank(operation.entityId(), extractEntityId(responseJson));
        String workspaceKey = firstNonBlank(operation.workspaceKey(), extractWorkspaceKey(responseJson));
        FailureDetail failureDetail = failureDetail(responseJson, failure, success);
        Map<String, Object> metadata = metadataOf(request, response, operation, responseJson, failure);

        auditService.record(new OperatorAuditEvent(
            null,
            Instant.now(),
            actorFor(request, actorBeforeChain),
            operation.eventType(success),
            success ? "success" : "failure",
            RequestContext.requestId(request),
            request.getMethod(),
            pathOf(request),
            operation.entityType(),
            entityId,
            workspaceKey,
            status,
            failureDetail.code(),
            failureDetail.message(),
            remoteAddr(request),
            request.getHeader("User-Agent"),
            metadata
        ));
    }

    private AuditOperation resolveOperation(HttpServletRequest request) {
        String method = request.getMethod();
        if (!isUnsafeMethod(method)) {
            return null;
        }
        String path = pathOf(request);
        if (!path.startsWith("/api/") || isAuditExcluded(path)) {
            return null;
        }

        List<String> segments = pathSegments(path);
        if (segments.size() < 2 || !"api".equals(segments.get(0))) {
            return null;
        }

        if ("chat-runs".equals(segment(segments, 1))) {
            return resolveChatRun(method, segments);
        }
        if ("chat".equals(segment(segments, 1)) && "POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("chat.submit_compat", null, "chat_run", null, queryParam(request, "workspaceKey"));
        }
        if ("materials".equals(segment(segments, 1))) {
            return resolveMaterial(method, segments, queryParam(request, "workspaceKey"));
        }
        if ("instructions".equals(segment(segments, 1))) {
            return resolveInstruction(method, segments);
        }
        if ("knowledge-presets".equals(segment(segments, 1))) {
            return resolveKnowledge("knowledge_preset", "knowledge_preset", method, segments);
        }
        if ("knowledge-facets".equals(segment(segments, 1))) {
            return resolveKnowledge("knowledge_facet", "knowledge_facet", method, segments);
        }
        if ("reference".equals(segment(segments, 1))) {
            return resolveReference(method, segments);
        }
        if ("rag-projects".equals(segment(segments, 1))) {
            return resolveRagProject(method, segments);
        }
        if ("conversations".equals(segment(segments, 1))) {
            return resolveConversation(method, segments, queryParam(request, "workspaceKey"));
        }
        if ("memory-entries".equals(segment(segments, 1))) {
            return resolveMemoryEntry(method, segments, queryParam(request, "workspaceKey"));
        }
        if ("llm-providers".equals(segment(segments, 1))) {
            return resolveLlmProvider(method, segments);
        }
        return null;
    }

    private AuditOperation resolveChatRun(String method, List<String> segments) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("chat_run.submit", null, "chat_run", null, null);
        }
        if ("POST".equals(method) && segments.size() == 4 && "cancel".equals(segment(segments, 3))) {
            return new AuditOperation("chat_run.cancel", null, "chat_run", segment(segments, 2), null);
        }
        return null;
    }

    private AuditOperation resolveMaterial(String method, List<String> segments, String workspaceKey) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("material.create_text", null, "material", null, workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 3 && "upload".equals(segment(segments, 2))) {
            return new AuditOperation("material.upload", null, "material", null, workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 3 && "rechunk-active".equals(segment(segments, 2))) {
            return new AuditOperation("material.rechunk_active", null, "material_batch", null, workspaceKey);
        }
        if ("POST".equals(method)
            && segments.size() == 4
            && "rechunk-active".equals(segment(segments, 2))
            && "batch".equals(segment(segments, 3))) {
            return new AuditOperation("material.rechunk_active_batch", null, "material_batch", null, workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 4 && "versions".equals(segment(segments, 3))) {
            return new AuditOperation("material.upload_version", null, "material", segment(segments, 2), workspaceKey);
        }
        if ("PUT".equals(method) && segments.size() == 3) {
            return new AuditOperation("material.update", null, "material", segment(segments, 2), workspaceKey);
        }
        if ("DELETE".equals(method) && segments.size() == 3) {
            return new AuditOperation("material.delete", null, "material", segment(segments, 2), workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 4 && "reindex".equals(segment(segments, 3))) {
            return new AuditOperation("material.reindex", null, "material", segment(segments, 2), workspaceKey);
        }
        return null;
    }

    private AuditOperation resolveInstruction(String method, List<String> segments) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("instruction.create", null, "instruction", null, null);
        }
        if ("PUT".equals(method) && segments.size() == 3) {
            return new AuditOperation("instruction.update", null, "instruction", segment(segments, 2), null);
        }
        if ("DELETE".equals(method) && segments.size() == 3) {
            return new AuditOperation("instruction.delete", null, "instruction", segment(segments, 2), null);
        }
        if ("POST".equals(method) && segments.size() == 5 && "restore".equals(segment(segments, 3))) {
            return new AuditOperation("instruction.restore", null, "instruction", segment(segments, 2), null);
        }
        return null;
    }

    private AuditOperation resolveKnowledge(
        String eventPrefix,
        String entityType,
        String method,
        List<String> segments
    ) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation(eventPrefix + ".create", null, entityType, null, null);
        }
        if ("PUT".equals(method) && segments.size() == 3) {
            return new AuditOperation(eventPrefix + ".update", null, entityType, segment(segments, 2), null);
        }
        if ("DELETE".equals(method) && segments.size() == 3) {
            return new AuditOperation(eventPrefix + ".delete", null, entityType, segment(segments, 2), null);
        }
        if ("POST".equals(method) && segments.size() == 5 && "restore".equals(segment(segments, 3))) {
            return new AuditOperation(eventPrefix + ".restore", null, entityType, segment(segments, 2), null);
        }
        return null;
    }

    private AuditOperation resolveReference(String method, List<String> segments) {
        if ("workspaces".equals(segment(segments, 2))) {
            if ("POST".equals(method) && segments.size() == 3) {
                return new AuditOperation("reference_workspace.create", null, "reference_workspace", null, null);
            }
            if ("PUT".equals(method) && segments.size() == 4) {
                return new AuditOperation("reference_workspace.update", null, "reference_workspace", segment(segments, 3), segment(segments, 3));
            }
        }
        if ("projects".equals(segment(segments, 2))) {
            if ("POST".equals(method) && segments.size() == 3) {
                return new AuditOperation("reference_project.create", null, "reference_project", null, null);
            }
            if ("PUT".equals(method) && segments.size() == 4) {
                return new AuditOperation("reference_project.update", null, "reference_project", segment(segments, 3), null);
            }
        }
        return null;
    }

    private AuditOperation resolveRagProject(String method, List<String> segments) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("rag_project.create", null, "rag_project", null, null);
        }
        if ("PUT".equals(method) && segments.size() == 3) {
            return new AuditOperation("rag_project.update", null, "rag_project", segment(segments, 2), null);
        }
        return null;
    }

    private AuditOperation resolveConversation(String method, List<String> segments, String workspaceKey) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("conversation.create", null, "conversation", null, workspaceKey);
        }
        if ("PATCH".equals(method) && segments.size() == 3) {
            return new AuditOperation("conversation.update", null, "conversation", segment(segments, 2), workspaceKey);
        }
        return null;
    }

    private AuditOperation resolveMemoryEntry(String method, List<String> segments, String workspaceKey) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("memory_entry.create", null, "memory_entry", null, workspaceKey);
        }
        if ("PATCH".equals(method) && segments.size() == 3) {
            return new AuditOperation("memory_entry.update", null, "memory_entry", segment(segments, 2), workspaceKey);
        }
        if ("DELETE".equals(method) && segments.size() == 3) {
            return new AuditOperation("memory_entry.delete", null, "memory_entry", segment(segments, 2), workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 4 && "approve".equals(segment(segments, 3))) {
            return new AuditOperation("memory_entry.approve", null, "memory_entry", segment(segments, 2), workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 4 && "reject".equals(segment(segments, 3))) {
            return new AuditOperation("memory_entry.reject", null, "memory_entry", segment(segments, 2), workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 4 && "pin".equals(segment(segments, 3))) {
            return new AuditOperation("memory_entry.pin", null, "memory_entry", segment(segments, 2), workspaceKey);
        }
        if ("POST".equals(method) && segments.size() == 4 && "unpin".equals(segment(segments, 3))) {
            return new AuditOperation("memory_entry.unpin", null, "memory_entry", segment(segments, 2), workspaceKey);
        }
        return null;
    }

    private AuditOperation resolveLlmProvider(String method, List<String> segments) {
        if ("POST".equals(method) && segments.size() == 2) {
            return new AuditOperation("llm_provider.create", null, "llm_provider", null, null);
        }
        if ("PUT".equals(method) && segments.size() == 3) {
            return new AuditOperation("llm_provider.update", null, "llm_provider", segment(segments, 2), null);
        }
        if ("DELETE".equals(method) && segments.size() == 3) {
            return new AuditOperation("llm_provider.delete", null, "llm_provider", segment(segments, 2), null);
        }
        if ("POST".equals(method) && segments.size() == 4 && "probe".equals(segment(segments, 3))) {
            return new AuditOperation("llm_provider.probe", null, "llm_provider", segment(segments, 2), null);
        }
        if ("POST".equals(method) && segments.size() == 4 && "activate".equals(segment(segments, 3))) {
            return new AuditOperation("llm_provider.activate", null, "llm_provider", segment(segments, 2), null);
        }
        if ("POST".equals(method)
            && segments.size() == 4
            && "fallback".equals(segment(segments, 2))
            && "activate".equals(segment(segments, 3))) {
            return new AuditOperation("llm_provider.activate_fallback", null, "llm_provider", "fallback", null);
        }
        return null;
    }

    private boolean isAuditExcluded(String path) {
        return path.equals("/api/search")
            || path.equals("/api/client-events")
            || path.equals("/api/liveness")
            || path.startsWith("/api/auth/");
    }

    private boolean isUnsafeMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String actorFor(HttpServletRequest request, String actorBeforeChain) {
        String actor = RequestContext.currentActor(request);
        if (RequestContext.isAuthenticatedActor(actor)) {
            return actor;
        }
        return actorBeforeChain;
    }

    private Map<String, Object> metadataOf(
        HttpServletRequest request,
        ContentCachingResponseWrapper response,
        AuditOperation operation,
        JsonNode responseJson,
        Throwable failure
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contentType", request.getContentType());
        metadata.put("query", request.getQueryString());
        metadata.put("responseBytes", response.getContentAsByteArray().length);
        metadata.put("routeEventType", operation.eventType());
        if (failure != null) {
            metadata.put("exceptionClass", failure.getClass().getName());
        }
        JsonNode statusNode = responseJson == null ? null : responseJson.get("status");
        if (statusNode != null && statusNode.isValueNode()) {
            metadata.put("responseStatus", statusNode.asText());
        }
        return metadata;
    }

    private JsonNode readResponseJson(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0 || content.length > MAX_RESPONSE_CAPTURE_BYTES) {
            return null;
        }
        String contentType = response.getContentType();
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (IOException exception) {
            return null;
        }
    }

    private FailureDetail failureDetail(JsonNode responseJson, Throwable failure, boolean success) {
        if (success) {
            return new FailureDetail(null, null);
        }
        String code = textField(responseJson, "code");
        String message = textField(responseJson, "message");
        if (!StringUtils.hasText(code) && failure != null) {
            code = failure.getClass().getSimpleName();
        }
        if (!StringUtils.hasText(message) && failure != null) {
            message = rootMessage(failure);
        }
        return new FailureDetail(code, message);
    }

    private String extractEntityId(JsonNode responseJson) {
        if (responseJson == null || responseJson.isMissingNode() || responseJson.isNull()) {
            return null;
        }
        for (String field : ENTITY_ID_RESPONSE_FIELDS) {
            String value = textField(responseJson, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String extractWorkspaceKey(JsonNode responseJson) {
        String value = textField(responseJson, "workspaceKey");
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (responseJson != null && responseJson.has("metadata")) {
            return textField(responseJson.get("metadata"), "workspaceKey");
        }
        return null;
    }

    private String textField(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private String pathOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private List<String> pathSegments(String path) {
        return Arrays.stream(path.split("/"))
            .filter(StringUtils::hasText)
            .map(this::decode)
            .toList();
    }

    private String queryParam(HttpServletRequest request, String name) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return null;
        }
        try {
            return UriComponentsBuilder.fromPath("/")
                .query(queryString)
                .build()
                .getQueryParams()
                .getFirst(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String segment(List<String> segments, int index) {
        return index >= 0 && index < segments.size() ? segments.get(index) : null;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String remoteAddr(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record AuditOperation(
        String eventType,
        String failureEventType,
        String entityType,
        String entityId,
        String workspaceKey
    ) {
        String eventType(boolean success) {
            return success || !StringUtils.hasText(failureEventType) ? eventType : failureEventType;
        }
    }

    private record FailureDetail(String code, String message) {
    }
}
