package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String ACTOR_MDC_KEY = "actor";
    public static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";
    public static final String ACTOR_ATTRIBUTE = RequestContext.class.getName() + ".actor";

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private RequestContext() {
    }

    public static String ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = requestId(request);
        if (request != null) {
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        }
        if (response != null) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
        }
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        return requestId;
    }

    public static String requestId(HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(REQUEST_ID_ATTRIBUTE);
            if (attribute instanceof String value && isValidRequestId(value)) {
                return value;
            }
            String headerValue = request.getHeader(REQUEST_ID_HEADER);
            if (isValidRequestId(headerValue)) {
                return headerValue.trim();
            }
        }
        String current = MDC.get(REQUEST_ID_MDC_KEY);
        if (isValidRequestId(current)) {
            return current;
        }
        return UUID.randomUUID().toString();
    }

    public static boolean isValidRequestId(String value) {
        return StringUtils.hasText(value) && REQUEST_ID_PATTERN.matcher(value.trim()).matches();
    }

    public static String currentActor() {
        return actorOf(SecurityContextHolder.getContext().getAuthentication());
    }

    public static String currentActor(HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(ACTOR_ATTRIBUTE);
            if (attribute instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
        }
        return currentActor();
    }

    public static void setActor(HttpServletRequest request, String actor) {
        String normalizedActor = StringUtils.hasText(actor) ? actor.trim() : "anonymous";
        if (request != null) {
            request.setAttribute(ACTOR_ATTRIBUTE, normalizedActor);
        }
        MDC.put(ACTOR_MDC_KEY, normalizedActor);
    }

    public static boolean isAuthenticatedActor(String actor) {
        return StringUtils.hasText(actor) && !"anonymous".equals(actor);
    }

    public static String actorOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) {
            return "anonymous";
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) ? name : "authenticated";
    }
}
