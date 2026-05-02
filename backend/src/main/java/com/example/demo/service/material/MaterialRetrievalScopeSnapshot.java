package com.example.demo.service.material;

public record MaterialRetrievalScopeSnapshot(
    int materialCount,
    int activeMaterialCount,
    int readyMaterialCount,
    int scopedMaterialCount,
    int scopedActiveMaterialCount,
    int scopedReadyMaterialCount
) {
}
