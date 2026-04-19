package com.example.demo.infrastructure.material;

public interface MaterialLineageRepository {

    String resolveSourceKey(MaterialLineageIdentity identity);

    void lockLineage(String sourceKey);
}
