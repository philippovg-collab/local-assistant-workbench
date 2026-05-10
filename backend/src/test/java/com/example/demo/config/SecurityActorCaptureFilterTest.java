package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityActorCaptureFilterTest {

    private final SecurityActorCaptureFilter filter = new SecurityActorCaptureFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void retainsActorWhenLogoutClearsSecurityContextDownstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticateAs("admin");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            SecurityContextHolder.clearContext()
        );

        assertEquals("admin", request.getAttribute(RequestContext.ACTOR_ATTRIBUTE));
    }

    @Test
    void capturesActorCreatedBySuccessfulLoginDownstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            authenticateAs("admin")
        );

        assertEquals("admin", request.getAttribute(RequestContext.ACTOR_ATTRIBUTE));
    }

    @Test
    void keepsAnonymousWhenNoAuthenticationAppears() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertEquals("anonymous", request.getAttribute(RequestContext.ACTOR_ATTRIBUTE));
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            username,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
    }
}
