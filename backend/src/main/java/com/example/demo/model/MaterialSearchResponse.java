package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialSearchResponse(
    String query,
    List<MaterialSearchHit> hits,
    MaterialSearchDebug debug
) {
    public MaterialSearchResponse {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
