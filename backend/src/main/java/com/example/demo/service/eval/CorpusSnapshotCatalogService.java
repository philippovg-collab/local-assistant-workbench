package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotDetail;
import com.example.demo.model.eval.CorpusSnapshotItemDetail;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CorpusSnapshotCatalogService {

    private final CorpusSnapshotRepository snapshotRepository;
    private final CorpusSnapshotService snapshotService;

    public CorpusSnapshotCatalogService(
        CorpusSnapshotRepository snapshotRepository,
        CorpusSnapshotService snapshotService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotService = snapshotService;
    }

    public List<CorpusSnapshot> listSnapshots() {
        return snapshotRepository.findSnapshots();
    }

    public CorpusSnapshot getSnapshot(String id) {
        return snapshotRepository.findSnapshot(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "corpus_snapshot.not_found",
                "Corpus snapshot '" + id + "' does not exist"
            ));
    }

    public CorpusSnapshotDetail getSnapshotDetail(String id) {
        return snapshotService.detail(getSnapshot(id));
    }

    public List<CorpusSnapshotItemDetail> getSnapshotItems(String id, int offset, int limit) {
        getSnapshot(id);
        return snapshotService.itemDetails(id, offset, limit);
    }
}
