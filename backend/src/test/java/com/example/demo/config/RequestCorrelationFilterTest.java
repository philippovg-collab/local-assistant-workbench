package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesRequestIdAndExposesItThroughHeaderAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            requestIdSeenInChain.set(MDC.get("requestId"));
            servletResponse.setContentType("application/json");
        });

        String responseRequestId = response.getHeader(RequestContext.REQUEST_ID_HEADER);
        assertTrue(RequestContext.isValidRequestId(responseRequestId));
        assertEquals(responseRequestId, requestIdSeenInChain.get());
        assertEquals(responseRequestId, request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void preservesValidIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            requestIdSeenInChain.set(MDC.get("requestId"))
        );

        assertEquals("req-123", response.getHeader(RequestContext.REQUEST_ID_HEADER));
        assertEquals("req-123", requestIdSeenInChain.get());
    }

    @Test
    void replacesInvalidIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "invalid request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String responseRequestId = response.getHeader(RequestContext.REQUEST_ID_HEADER);
        assertTrue(RequestContext.isValidRequestId(responseRequestId));
        assertTrue(!"invalid request id".equals(responseRequestId));
    }

    @Test
    void writesAccessLogWithoutRequestBody() throws Exception {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = startListAppender(accessLogger);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
            request.setContent("{\"password\":\"secret-value\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(201)
            );

            assertEquals(1, appender.list.size());
            String message = appender.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("method=POST"));
            assertTrue(message.contains("path=/api/materials"));
            assertTrue(message.contains("status=201"));
            assertTrue(!message.contains("secret-value"));
        } finally {
            accessLogger.detachAppender(appender);
        }
    }

    @Test
    void writesAccessLogWithCapturedActor() throws Exception {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = startListAppender(accessLogger);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                RequestContext.setActor((MockHttpServletRequest) servletRequest, "admin")
            );

            assertEquals(1, appender.list.size());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("actor=admin"));
        } finally {
            accessLogger.detachAppender(appender);
        }
    }

    @Test
    void suppressesLivenessInfoAccessLog() throws Exception {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = startListAppender(accessLogger);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/liveness");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertTrue(appender.list.isEmpty());
        } finally {
            accessLogger.detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> startListAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
