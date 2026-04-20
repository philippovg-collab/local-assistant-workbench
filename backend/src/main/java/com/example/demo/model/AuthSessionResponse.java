package com.example.demo.model;

import java.util.List;

public record AuthSessionResponse(
    boolean authenticated,
    String username,
    List<String> roles,
    String csrfHeaderName,
    String csrfToken
) {

    public AuthSessionResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
