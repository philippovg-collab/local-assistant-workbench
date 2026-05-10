package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger accessLogger = LoggerFactory.getLogger("http.access");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isApiRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant startedAt = Instant.now();
        String method = request.getMethod();
        String path = request.getRequestURI();
        RequestContext.ensureRequestId(request, response);
        MDC.put("method", method);
        MDC.put("path", path);

        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            int status = response.getStatus();
            if (failure != null && status < 400) {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            MDC.put("status", String.valueOf(status));
            MDC.put("latencyMs", String.valueOf(latencyMs));
            String actor = RequestContext.currentActor(request);
            MDC.put(RequestContext.ACTOR_MDC_KEY, actor);
            if (!isQuietLiveness(request)) {
                accessLogger.info(
                    "HTTP access method={} path={} status={} latencyMs={} actor={}",
                    method,
                    path,
                    status,
                    latencyMs,
                    actor
                );
            }
            clearMdc();
        }
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI() != null && request.getRequestURI().startsWith("/api/");
    }

    private boolean isQuietLiveness(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) && "/api/liveness".equals(request.getRequestURI());
    }

    private void clearMdc() {
        MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        MDC.remove(RequestContext.ACTOR_MDC_KEY);
        MDC.remove("method");
        MDC.remove("path");
        MDC.remove("status");
        MDC.remove("latencyMs");
    }
}
