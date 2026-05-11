package com.example.demo.service.eval.port;

import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotItem;
import java.util.List;
import java.util.Optional;

public interface CorpusSnapshotRepository {

    List<CorpusSnapshot> findSnapshots();

    Optional<CorpusSnapshot> findSnapshot(String id);

    CorpusSnapshot saveSnapshot(CorpusSnapshot snapshot);

    CorpusSnapshotItem saveItem(CorpusSnapshotItem item);

    List<CorpusSnapshotItem> findItemsBySnapshotId(String snapshotId);

    List<CorpusSnapshotItem> findItemsBySnapshotId(String snapshotId, int offset, int limit);

    boolean isStorageReady();
}
