package com.example.demo.model.eval;

import com.example.demo.model.EvidenceLocator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateEvalCaseRequest(
    @NotBlank
    @Size(max = 128)
    String caseKey,
    @NotNull
    EvalCaseType caseType,
    EvalExpectedMode expectedMode,
    EvalCaseSeverity severity,
    @NotBlank
    @Size(max = 10_000)
    String question,
    Map<String, Object> knowledgeScope,
    Map<String, Object> retrievalFilters,
    @Size(max = 128)
    List<@Size(max = 1_000) String> goldFacts,
    @Size(max = 128)
    List<@Size(max = 1_000) String> acceptedAnswers,
    @Size(max = 128)
    List<EvidenceLocator> goldEvidenceLocators,
    @Size(max = 64)
    List<List<EvidenceLocator>> requiredDocGroups,
    @Size(max = 128)
    List<@Size(max = 256) String> forbiddenDocumentRefs,
    @Size(max = 64)
    List<@Size(max = 64) String> tags,
    EvalCaseOrigin origin
) {
    public CreateEvalCaseRequest {
        knowledgeScope = knowledgeScope == null ? Map.of() : Map.copyOf(knowledgeScope);
        retrievalFilters = retrievalFilters == null ? Map.of() : Map.copyOf(retrievalFilters);
        goldFacts = goldFacts == null ? List.of() : List.copyOf(goldFacts);
        acceptedAnswers = acceptedAnswers == null ? List.of() : List.copyOf(acceptedAnswers);
        goldEvidenceLocators = goldEvidenceLocators == null ? List.of() : List.copyOf(goldEvidenceLocators);
        requiredDocGroups = requiredDocGroups == null ? List.of() : List.copyOf(requiredDocGroups);
        forbiddenDocumentRefs = forbiddenDocumentRefs == null ? List.of() : List.copyOf(forbiddenDocumentRefs);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
