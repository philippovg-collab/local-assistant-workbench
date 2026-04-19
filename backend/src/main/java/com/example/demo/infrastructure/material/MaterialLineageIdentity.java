package com.example.demo.infrastructure.material;

public record MaterialLineageIdentity(
    String sourceType,
    MaterialLineageIdentityKind identityKind,
    String identityKey,
    String explicitTitleNorm,
    String originalFileNameNorm,
    String fileStemNorm,
    String contentAnchor,
    String sourceKey
) {
}
