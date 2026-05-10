package com.example.demo.model;

public record RetrievalQueryReferencedSource(
    Integer sourceIndex,
    Integer documentIndex,
    String materialId,
    String title,
    String documentNumber,
    String project,
    String counterparty,
    Integer page
) {
}
