package com.example.demo.service.eval.port;

import com.example.demo.service.eval.EvalCorpusMaterial;
import java.util.List;
import java.util.Map;

public interface EvalCorpusReader {

    List<EvalCorpusMaterial> readSnapshotMaterials(boolean includeSuperseded);

    Map<String, Object> readRevisionPins();

    List<String> findMissingRevisionPins(Map<String, Object> revisionPins);
}
