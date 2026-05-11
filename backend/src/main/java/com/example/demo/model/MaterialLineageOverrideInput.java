package com.example.demo.model;

public record MaterialLineageOverrideInput(
    String lineageKey,
    String reason,
    Boolean confirmSupersedeExistingLineage
) {
    public boolean confirmed() {
        return Boolean.TRUE.equals(confirmSupersedeExistingLineage);
    }
}
