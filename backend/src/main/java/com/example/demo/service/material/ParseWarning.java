package com.example.demo.service.material;

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
