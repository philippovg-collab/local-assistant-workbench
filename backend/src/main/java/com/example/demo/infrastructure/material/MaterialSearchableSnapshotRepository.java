package com.example.demo.infrastructure.material;

import java.util.List;

public interface MaterialSearchableSnapshotRepository {

    SearchableMaterialSnapshot resolveSearchableSnapshot(String materialId);

    List<String> findAllSearchableMaterialIds();
}
