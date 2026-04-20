package com.example.demo.service.material.port;

import com.example.demo.service.material.SearchableMaterialSnapshot;

import java.util.List;

public interface MaterialSearchableSnapshotRepository {

    SearchableMaterialSnapshot resolveSearchableSnapshot(String materialId);

    List<String> findAllSearchableMaterialIds();
}
