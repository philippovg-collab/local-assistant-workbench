package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.service.eval.port.EvalRunRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvalRunCatalogService {

    private final EvalRunRepository runRepository;

    public EvalRunCatalogService(EvalRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public List<EvalRun> listRuns() {
        return runRepository.findRuns();
    }

    public EvalRun getRun(String id) {
        return runRepository.findRun(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_run.not_found",
                "Eval run '" + id + "' does not exist"
            ));
    }

    public List<EvalRunItemArtifact> getRunItemArtifacts(String runId, String itemId) {
        EvalRunItem item = runRepository.findItem(itemId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_run_item.not_found",
                "Eval run item '" + itemId + "' does not exist"
            ));
        if (!item.runId().equals(runId)) {
            throw new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_run_item.not_found",
                "Eval run item '" + itemId + "' does not exist in eval run '" + runId + "'"
            );
        }
        return runRepository.findArtifactsByItemId(itemId);
    }
}
