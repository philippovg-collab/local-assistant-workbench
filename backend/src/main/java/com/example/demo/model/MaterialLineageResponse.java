package com.example.demo.model;

import java.util.List;

public record MaterialLineageResponse(
    String requestedMaterialId,
    String activeMaterialId,
    List<MaterialLineageVersion> versions,
    MaterialLineageOverrideInfo lineageOverride
) {
    public MaterialLineageResponse(
        String requestedMaterialId,
        String activeMaterialId,
        List<MaterialLineageVersion> versions
    ) {
        this(requestedMaterialId, activeMaterialId, versions, null);
    }
}
