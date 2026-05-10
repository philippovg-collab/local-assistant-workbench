package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.config.RequestContext;
import com.example.demo.model.AuthLoginRequest;
import com.example.demo.model.AuthSessionResponse;
import com.example.demo.service.OperatorAuditService;
import com.example.demo.service.audit.OperatorAuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final ObjectProvider<OperatorAuditService> operatorAuditServiceProvider;

    public AuthController(
        AuthenticationManager authenticationManager,
        SecurityContextRepository securityContextRepository,
        ObjectProvider<OperatorAuditService> operatorAuditServiceProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.operatorAuditServiceProvider = operatorAuditServiceProvider;
    }

    @GetMapping("/session")
    public AuthSessionResponse session(
        Authentication authentication,
        CsrfToken csrfToken,
        HttpServletResponse servletResponse
    ) {
        preventAuthResponseCaching(servletResponse);
        return toSessionResponse(authentication, csrfToken);
    }

    @PostMapping("/login")
    public AuthSessionResponse login(
        @RequestBody AuthLoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse,
        CsrfToken csrfToken
    ) {
        preventAuthResponseCaching(servletResponse);
        if (request == null
            || !StringUtils.hasText(request.username())
            || !StringUtils.hasText(request.password())) {
            recordAuthAudit(
                servletRequest,
                attemptedActor(request),
                "auth.login_failed",
                "failure",
                HttpStatus.BAD_REQUEST.value(),
                "auth.invalid_request",
                "Username and password are required"
            );
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "auth.invalid_request",
                "Username and password are required"
            );
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    request.username().trim(),
                    request.password()
                )
            );
        } catch (BadCredentialsException exception) {
            recordAuthAudit(
                servletRequest,
                attemptedActor(request),
                "auth.login_failed",
                "failure",
                HttpStatus.UNAUTHORIZED.value(),
                "auth.invalid_credentials",
                "Invalid username or password"
            );
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "auth.invalid_credentials",
                "Invalid username or password",
                exception
            );
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        String actor = RequestContext.actorOf(authentication);
        RequestContext.setActor(servletRequest, actor);
        recordAuthAudit(
            servletRequest,
            actor,
            "auth.login",
            "success",
            HttpStatus.OK.value(),
            null,
            null
        );
        return toSessionResponse(authentication, csrfToken);
    }

    @PostMapping("/logout")
    public AuthSessionResponse logout(
        Authentication authentication,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse,
        CsrfToken csrfToken
    ) {
        preventAuthResponseCaching(servletResponse);
        String actor = RequestContext.currentActor(servletRequest);
        new SecurityContextLogoutHandler().logout(servletRequest, servletResponse, authentication);
        recordAuthAudit(
            servletRequest,
            actor,
            "auth.logout",
            "success",
            HttpStatus.OK.value(),
            null,
            null
        );
        return new AuthSessionResponse(false, null, List.of(), csrfToken.getHeaderName(), csrfToken.getToken());
    }

    private void recordAuthAudit(
        HttpServletRequest request,
        String actor,
        String eventType,
        String outcome,
        int statusCode,
        String failureCode,
        String failureMessage
    ) {
        OperatorAuditService auditService = operatorAuditServiceProvider.getIfAvailable();
        if (auditService == null) {
            return;
        }
        auditService.record(new OperatorAuditEvent(
            null,
            Instant.now(),
            StringUtils.hasText(actor) ? actor.trim() : "anonymous",
            eventType,
            outcome,
            RequestContext.requestId(request),
            request.getMethod(),
            pathOf(request),
            "auth_session",
            null,
            null,
            statusCode,
            failureCode,
            failureMessage,
            remoteAddr(request),
            request.getHeader("User-Agent"),
            authMetadata(failureCode, statusCode)
        ));
    }

    private Map<String, Object> authMetadata(String failureCode, int statusCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("statusCode", statusCode);
        if (StringUtils.hasText(failureCode)) {
            metadata.put("failureCode", failureCode);
        }
        return metadata;
    }

    private String attemptedActor(AuthLoginRequest request) {
        if (request != null && StringUtils.hasText(request.username())) {
            return request.username().trim();
        }
        return "anonymous";
    }

    private String pathOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private String remoteAddr(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void preventAuthResponseCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private AuthSessionResponse toSessionResponse(Authentication authentication, CsrfToken csrfToken) {
        boolean authenticated = authentication != null
            && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal());
        if (!authenticated) {
            return new AuthSessionResponse(false, null, List.of(), csrfToken.getHeaderName(), csrfToken.getToken());
        }

        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
        return new AuthSessionResponse(
            true,
            authentication.getName(),
            roles,
            csrfToken.getHeaderName(),
            csrfToken.getToken()
        );
    }
}
