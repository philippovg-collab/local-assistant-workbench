package com.example.demo.model.eval;

import com.example.demo.model.AnswerMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateE2EEvalRunRequest(
    @NotBlank
    @Size(max = 36)
    String datasetId,
    @Size(max = 128)
    String datasetVersion,
    @Size(max = 200)
    List<@Size(max = 128) String> caseIds,
    @Size(max = 36)
    String corpusSnapshotId,
    @Size(max = 128)
    String executionConfigHash,
    Instant referenceInstant,
    @Size(max = 128)
    String model,
    AnswerMode answerMode,
    EvalJudgeMode judgeMode,
    @Min(1)
    @Max(200)
    Integer limit,
    @Size(max = 32)
    List<@Size(max = 64) String> tags
) {
    public CreateE2EEvalRunRequest {
        caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
        judgeMode = judgeMode == null ? EvalJudgeMode.DETERMINISTIC_ONLY : judgeMode;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
