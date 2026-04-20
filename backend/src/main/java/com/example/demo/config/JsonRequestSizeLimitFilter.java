package com.example.demo.config;

import com.example.demo.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JsonRequestSizeLimitFilter extends OncePerRequestFilter {

    private final RequestLimitProperties properties;
    private final ObjectMapper objectMapper;

    public JsonRequestSizeLimitFilter(RequestLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!shouldLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = Math.max(0, properties.getMaxJsonBytes());
        if (request.getContentLengthLong() > limit) {
            writeTooLarge(response);
            return;
        }

        try {
            filterChain.doFilter(new LimitedJsonRequest(request, limit), response);
        } catch (RequestPayloadTooLargeException exception) {
            writeTooLarge(response);
        }
    }

    private boolean shouldLimit(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return false;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType != null
            && contentType.toLowerCase(java.util.Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
            "request.payload_too_large",
            "JSON request body exceeds the configured size limit",
            Instant.now().toString(),
            null
        ));
    }

    private static final class LimitedJsonRequest extends HttpServletRequestWrapper {

        private final int limit;

        private LimitedJsonRequest(HttpServletRequest request, int limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream(), limit);
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final int limit;
        private long readBytes = 0;

        private LimitedServletInputStream(ServletInputStream delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                increment(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                increment(count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void increment(int count) throws RequestPayloadTooLargeException {
            readBytes += count;
            if (readBytes > limit) {
                throw new RequestPayloadTooLargeException();
            }
        }
    }

    public static class RequestPayloadTooLargeException extends IOException {
    }
}
