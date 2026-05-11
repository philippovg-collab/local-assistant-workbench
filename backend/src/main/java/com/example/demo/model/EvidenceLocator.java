package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceLocator(
    String sourceKey,
    String materialId,
    String documentNumber,
    String versionLabel,
    MaterialVersionState versionState,
    Integer lineageVersion,
    Integer chunkIndex,
    Integer page,
    List<String> sectionPath,
    List<String> headingTrail,
    String tableId,
    String slideId,
    String rowKey,
    String columnKey,
    Integer spanStart,
    Integer spanEnd
) {
    public EvidenceLocator {
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        headingTrail = headingTrail == null ? List.of() : List.copyOf(headingTrail);
    }
}
