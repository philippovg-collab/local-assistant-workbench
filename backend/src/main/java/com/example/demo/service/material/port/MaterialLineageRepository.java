package com.example.demo.service.material.port;

import com.example.demo.service.material.MaterialLineageIdentity;

public interface MaterialLineageRepository {

    String resolveSourceKey(MaterialLineageIdentity identity);

    void lockLineage(String sourceKey);
}
