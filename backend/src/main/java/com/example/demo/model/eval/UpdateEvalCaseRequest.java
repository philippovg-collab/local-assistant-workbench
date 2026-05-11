package com.example.demo.model.eval;

import com.example.demo.model.EvidenceLocator;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpdateEvalCaseRequest(
    EvalCaseType caseType,
    EvalExpectedMode expectedMode,
    EvalCaseSeverity severity,
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
    public UpdateEvalCaseRequest {
        knowledgeScope = knowledgeScope == null ? null : Map.copyOf(knowledgeScope);
        retrievalFilters = retrievalFilters == null ? null : Map.copyOf(retrievalFilters);
        goldFacts = goldFacts == null ? null : List.copyOf(goldFacts);
        acceptedAnswers = acceptedAnswers == null ? null : List.copyOf(acceptedAnswers);
        goldEvidenceLocators = goldEvidenceLocators == null ? null : List.copyOf(goldEvidenceLocators);
        requiredDocGroups = requiredDocGroups == null ? null : List.copyOf(requiredDocGroups);
        forbiddenDocumentRefs = forbiddenDocumentRefs == null ? null : List.copyOf(forbiddenDocumentRefs);
        tags = tags == null ? null : List.copyOf(tags);
    }
}
