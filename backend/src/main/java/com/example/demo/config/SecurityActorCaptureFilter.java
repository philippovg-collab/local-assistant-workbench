package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class SecurityActorCaptureFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        RequestContext.setActor(request, RequestContext.currentActor());
        try {
            filterChain.doFilter(request, response);
        } finally {
            String actorAfterChain = RequestContext.currentActor();
            if (RequestContext.isAuthenticatedActor(actorAfterChain)) {
                RequestContext.setActor(request, actorAfterChain);
            }
        }
    }
}
