package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.model.AuthLoginRequest;
import com.example.demo.model.AuthSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
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

    public AuthController(
        AuthenticationManager authenticationManager,
        SecurityContextRepository securityContextRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/session")
    public AuthSessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        return toSessionResponse(authentication, csrfToken);
    }

    @PostMapping("/login")
    public AuthSessionResponse login(
        @RequestBody AuthLoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse,
        CsrfToken csrfToken
    ) {
        if (request == null
            || !StringUtils.hasText(request.username())
            || !StringUtils.hasText(request.password())) {
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
        return toSessionResponse(authentication, csrfToken);
    }

    @PostMapping("/logout")
    public AuthSessionResponse logout(
        Authentication authentication,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse,
        CsrfToken csrfToken
    ) {
        new SecurityContextLogoutHandler().logout(servletRequest, servletResponse, authentication);
        return new AuthSessionResponse(false, null, List.of(), csrfToken.getHeaderName(), csrfToken.getToken());
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
