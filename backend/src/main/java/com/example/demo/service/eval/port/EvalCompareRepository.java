package com.example.demo.service.eval.port;

import com.example.demo.model.eval.EvalRunCompare;
import java.util.List;
import java.util.Optional;

public interface EvalCompareRepository {

    List<EvalRunCompare> findCompares();

    Optional<EvalRunCompare> findCompare(String id);

    EvalRunCompare saveCompare(EvalRunCompare compare);

    boolean isStorageReady();
}
