package com.example.demo.model;

public record RevisionDiffEntry(
    String field,
    String fromValue,
    String toValue
) {
}
