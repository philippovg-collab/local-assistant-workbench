package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseRevision;
import org.springframework.stereotype.Component;

@Component
public class EvalCaseRevisionMapper {

    public EvalCase toEvalCase(EvalCaseRevision revision) {
        if (revision == null || revision.caseSnapshot() == null) {
            throw new IllegalArgumentException("Eval case revision snapshot is required");
        }
        return revision.caseSnapshot();
    }
}
