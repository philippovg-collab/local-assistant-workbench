package com.example.demo.infrastructure.material;

import java.util.Set;

public record MaterialRetrievalScopeSnapshot(
    int materialCount,
    int activeMaterialCount,
    int readyMaterialCount,
    int scopedMaterialCount,
    int scopedActiveMaterialCount,
    int scopedReadyMaterialCount,
    Set<String> scopedReadyMaterialIds
) {

    public MaterialRetrievalScopeSnapshot {
        scopedReadyMaterialIds = scopedReadyMaterialIds == null ? Set.of() : Set.copyOf(scopedReadyMaterialIds);
    }
}
