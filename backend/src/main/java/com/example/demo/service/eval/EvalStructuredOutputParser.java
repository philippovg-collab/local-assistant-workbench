package com.example.demo.service.eval;

import com.example.demo.model.eval.AnswerCitation;
import com.example.demo.model.eval.AnswerClaim;
import com.example.demo.model.eval.EvalStructuredAnswer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalStructuredOutputParser {

    private final ObjectMapper objectMapper;

    public EvalStructuredOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvalStructuredAnswer parse(String rawOutput) {
        String json = extractJson(rawOutput);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new EvalOutputFormatException("Eval output is not valid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new EvalOutputFormatException("Eval output must be a JSON object");
        }
        String answer = requiredText(root, "answer");
        String finalMode = requiredText(root, "finalMode");
        if (!List.of("answered", "abstained", "clarification_requested").contains(finalMode)) {
            throw new EvalOutputFormatException("Field 'finalMode' has an unsupported value");
        }
        JsonNode claimsNode = root.get("claims");
        if (claimsNode == null || !claimsNode.isArray()) {
            throw new EvalOutputFormatException("Field 'claims' must be an array");
        }
        List<AnswerClaim> claims = new ArrayList<>();
        Iterator<JsonNode> claimIterator = claimsNode.elements();
        while (claimIterator.hasNext()) {
            JsonNode claimNode = claimIterator.next();
            if (!claimNode.isObject()) {
                throw new EvalOutputFormatException("Each claim must be an object");
            }
            String claimId = requiredText(claimNode, "claimId");
            String text = requiredText(claimNode, "text");
            JsonNode citationsNode = claimNode.get("citations");
            if (citationsNode == null || !citationsNode.isArray()) {
                throw new EvalOutputFormatException("Field 'citations' must be an array");
            }
            List<AnswerCitation> citations = new ArrayList<>();
            Iterator<JsonNode> citationIterator = citationsNode.elements();
            while (citationIterator.hasNext()) {
                JsonNode citationNode = citationIterator.next();
                JsonNode sourceIdNode = citationNode.get("sourceId");
                if (sourceIdNode == null || !sourceIdNode.canConvertToInt() || sourceIdNode.asInt() < 1) {
                    throw new EvalOutputFormatException("Citation sourceId must be a positive integer");
                }
                citations.add(new AnswerCitation(sourceIdNode.asInt(), null));
            }
            claims.add(new AnswerClaim(claimId, text, citations));
        }
        return new EvalStructuredAnswer(answer, finalMode, claims);
    }

    private String extractJson(String rawOutput) {
        if (!StringUtils.hasText(rawOutput)) {
            throw new EvalOutputFormatException("Eval output is empty");
        }
        String trimmed = rawOutput.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        throw new EvalOutputFormatException("Eval output must be a raw JSON object");
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new EvalOutputFormatException("Field '" + fieldName + "' is required");
        }
        return value.asText();
    }
}
