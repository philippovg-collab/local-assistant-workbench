package com.example.demo.service.eval;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextOptions;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.eval.CreateE2EEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalChatRunRequestFactory {

    static final String STRUCTURED_OUTPUT_INSTRUCTION = """
        EVAL OUTPUT CONTRACT:
        Return exactly one JSON object and no extra prose, markdown, or commentary.
        The JSON object must match this shape:
        {
          "answer": "string",
          "finalMode": "answered|abstained|clarification_requested",
          "claims": [
            {
              "claimId": "c1",
              "text": "string",
              "citations": [{ "sourceId": 1 }]
            }
          ]
        }
        Use sourceId values from the numbered sources provided by the retrieval context. If there is no answer, set finalMode to "abstained" and keep claims empty. If clarification is needed, set finalMode to "clarification_requested" and keep claims empty.
        """;

    private final ObjectMapper objectMapper;

    public EvalChatRunRequestFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatExecutionRequest create(EvalCase evalCase, CreateE2EEvalRunRequest request) {
        return new ChatExecutionRequest(
            ChatMode.RAG,
            normalize(request.model()),
            evalCase.question(),
            null,
            List.of(),
            answerMode(evalCase, request),
            convertOrEmpty(evalCase.knowledgeScope(), KnowledgeScope.class, KnowledgeScope.empty()),
            instructionWorkspaceKey(evalCase),
            convertOrEmpty(evalCase.retrievalFilters(), RetrievalFilters.class, RetrievalFilters.empty()),
            List.of(),
            List.of(),
            STRUCTURED_OUTPUT_INSTRUCTION,
            null,
            null,
            null,
            false,
            true,
            isolatedContextOptions()
        );
    }

    private AnswerMode answerMode(EvalCase evalCase, CreateE2EEvalRunRequest request) {
        if (request.answerMode() != null) {
            return request.answerMode();
        }
        return AnswerMode.WITH_QUOTES;
    }

    private String instructionWorkspaceKey(EvalCase evalCase) {
        Object workspaceKey = evalCase.knowledgeScope().get("workspaceKey");
        return workspaceKey == null ? null : normalize(String.valueOf(workspaceKey));
    }

    private ContextOptions isolatedContextOptions() {
        return new ContextOptions(
            false,
            false,
            false,
            false,
            false,
            false,
            0,
            null
        );
    }

    private <T> T convertOrEmpty(Map<String, Object> rawValue, Class<T> type, T fallback) {
        if (rawValue == null || rawValue.isEmpty()) {
            return fallback;
        }
        return objectMapper.convertValue(rawValue, type);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
