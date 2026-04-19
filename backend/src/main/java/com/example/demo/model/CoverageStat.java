package com.example.demo.model;

public record CoverageStat(
    int covered,
    double ratio
) {
    public static CoverageStat empty() {
        return new CoverageStat(0, 0.0d);
    }
}
