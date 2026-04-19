package com.example.demo.infrastructure.material;

import java.util.List;

public record ParseWarning(
    String code,
    String message,
    List<Integer> pages
) {
    public ParseWarning {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
