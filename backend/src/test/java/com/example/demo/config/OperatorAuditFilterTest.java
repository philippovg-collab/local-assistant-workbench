package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.demo.api.OperatorAuditFilter;
import com.example.demo.service.AuditRedactionService;
import com.example.demo.service.OperatorAuditService;
import com.example.demo.service.audit.OperatorAuditEvent;
import com.example.demo.service.audit.port.OperatorAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OperatorAuditFilterTest {

    private final CapturingOperatorAuditRepository repository = new CapturingOperatorAuditRepository();
    private final OperatorAuditService auditService = new OperatorAuditService(
        repository,
        new AuditRedactionService(new ChatAuditProperties())
    );
    private final OperatorAuditFilter filter = new OperatorAuditFilter(
        providerOf(auditService),
        new ObjectMapper()
    );

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void auditsMaterialMutationWithResponseEntityAndWorkspace() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "req-material-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {"id":"material-1","metadata":{"workspaceKey":"workspace-a"}}
                """);
        });

        OperatorAuditEvent event = repository.onlyEvent();
        assertEquals("material.create_text", event.eventType());
        assertEquals("success", event.outcome());
        assertEquals("req-material-1", event.requestId());
        assertEquals("material", event.entityType());
        assertEquals("material-1", event.entityId());
        assertEquals("workspace-a", event.workspaceKey());
        assertEquals(201, event.statusCode());
    }

    @Test
    void skipsAuthBecauseControllerAuditsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(401);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {"code":"auth.invalid_credentials","message":"Invalid username or password"}
                """);
        });

        assertNull(repository.lastEvent());
    }

    @Test
    void usesCapturedRequestActor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            RequestContext.setActor((MockHttpServletRequest) servletRequest, "admin");
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {"id":"material-2"}
                """);
        });

        OperatorAuditEvent event = repository.onlyEvent();
        assertEquals("admin", event.actor());
    }

    @Test
    void readsWorkspaceKeyFromQueryWithoutTouchingRequestParameters() throws Exception {
        MockHttpServletRequest request = new ParameterFailingRequest("PUT", "/api/materials/material-1");
        request.setQueryString("workspaceKey=workspace-query");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(200);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {"id":"material-1"}
                """);
        });

        OperatorAuditEvent event = repository.onlyEvent();
        assertEquals("material.update", event.eventType());
        assertEquals("workspace-query", event.workspaceKey());
    }

    @Test
    void auditsConversationMutations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {"id":"conversation-1","workspaceKey":"workspace-a"}
                """);
        });

        OperatorAuditEvent event = repository.onlyEvent();
        assertEquals("conversation.create", event.eventType());
        assertEquals("conversation", event.entityType());
        assertEquals("conversation-1", event.entityId());
        assertEquals("workspace-a", event.workspaceKey());
    }

    @Test
    void auditsLlmProviderMutationWithoutRequestSecrets() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/llm-providers");
        request.setContentType("application/json");
        request.setContent("""
            {"name":"Corp Provider","apiKey":"secret-api-key"}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                {"id":"provider-1","hasApiKey":true}
                """);
        });

        OperatorAuditEvent event = repository.onlyEvent();
        assertEquals("llm_provider.create", event.eventType());
        assertEquals("llm_provider", event.entityType());
        assertEquals("provider-1", event.entityId());
        assertFalse(event.metadata().toString().contains("secret-api-key"));
        assertFalse(event.metadata().toString().contains("apiKey"));
    }

    @Test
    void skipsSearchBecauseItIsReadLikeQuery() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(repository.lastEvent());
    }

    private static ObjectProvider<OperatorAuditService> providerOf(OperatorAuditService service) {
        return new ObjectProvider<>() {
            @Override
            public OperatorAuditService getObject(Object... args) throws BeansException {
                return service;
            }

            @Override
            public OperatorAuditService getObject() throws BeansException {
                return service;
            }

            @Override
            public OperatorAuditService getIfAvailable() throws BeansException {
                return service;
            }

            @Override
            public OperatorAuditService getIfUnique() throws BeansException {
                return service;
            }
        };
    }

    private static final class CapturingOperatorAuditRepository implements OperatorAuditRepository {

        private final List<OperatorAuditEvent> events = new ArrayList<>();

        @Override
        public void save(OperatorAuditEvent event) {
            events.add(event);
        }

        @Override
        public int deleteEventsOlderThan(Instant cutoff) {
            return 0;
        }

        private OperatorAuditEvent onlyEvent() {
            assertEquals(1, events.size());
            return events.getFirst();
        }

        private OperatorAuditEvent lastEvent() {
            return events.isEmpty() ? null : events.getLast();
        }
    }

    private static final class ParameterFailingRequest extends MockHttpServletRequest {

        private ParameterFailingRequest(String method, String requestUri) {
            super(method, requestUri);
        }

        @Override
        public String getParameter(String name) {
            throw new AssertionError("Operator audit must not read request parameters");
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            throw new AssertionError("Operator audit must not read request parameters");
        }

        @Override
        public Enumeration<String> getParameterNames() {
            throw new AssertionError("Operator audit must not read request parameters");
        }

        @Override
        public String[] getParameterValues(String name) {
            throw new AssertionError("Operator audit must not read request parameters");
        }
    }
}
