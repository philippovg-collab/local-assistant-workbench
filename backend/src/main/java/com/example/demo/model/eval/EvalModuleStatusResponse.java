package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;

public record EvalModuleStatusResponse(
    boolean storageReady,
    boolean schemaReady,
    boolean readOnly,
    List<String> capabilities,
    List<String> endpoints,
    Instant checkedAt
) {
    public EvalModuleStatusResponse {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }
}
