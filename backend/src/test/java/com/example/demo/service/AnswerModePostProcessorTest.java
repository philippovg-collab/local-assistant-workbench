package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerModePostProcessorTest {

    private final AnswerModePostProcessor postProcessor =
        new AnswerModePostProcessor(new MaterialContentSupport(new MaterialProperties()));

    @Test
    void documentsOnlyRejectsUnsupportedAnswer() {
        MaterialRetrievalResult retrievalResult = retrievalResult("Тариф Премиум стоит 12000 тенге в месяц.");

        String processed = postProcessor.apply(
            AnswerMode.DOCUMENTS_ONLY,
            "Тариф Премиум стоит 15000 тенге в месяц.",
            retrievalResult
        );

        assertEquals("В документах нет достаточного подтверждения для такого вывода.", processed);
    }

    @Test
    void documentsOnlyKeepsSupportedAnswer() {
        MaterialRetrievalResult retrievalResult = retrievalResult("Тариф Премиум стоит 12000 тенге в месяц.");
        String answer = "Тариф Премиум стоит 12000 тенге в месяц.";

        String processed = postProcessor.apply(AnswerMode.DOCUMENTS_ONLY, answer, retrievalResult);

        assertEquals(answer, processed);
    }

    @Test
    void broaderReasoningLabelsUnsupportedExtension() {
        MaterialRetrievalResult retrievalResult = retrievalResult("Тариф Премиум стоит 12000 тенге в месяц.");
        String answer = "Тариф Премиум стоит 15000 тенге в месяц.";

        String processed = postProcessor.apply(AnswerMode.BROADER_REASONING, answer, retrievalResult);

        assertTrue(processed.contains(answer));
        assertTrue(processed.contains("Более широкое рассуждение:"));
    }

    @Test
    void broaderReasoningKeepsSupportedAnswer() {
        MaterialRetrievalResult retrievalResult = retrievalResult("Тариф Премиум стоит 12000 тенге в месяц.");
        String answer = "Тариф Премиум стоит 12000 тенге в месяц.";

        String processed = postProcessor.apply(AnswerMode.BROADER_REASONING, answer, retrievalResult);

        assertEquals(answer, processed);
    }

    private MaterialRetrievalResult retrievalResult(String contextText) {
        ChatSource source = new ChatSource(
            "material-1",
            "material-1:0",
            "Pricing FAQ",
            contextText,
            100,
            1.0d,
            List.of("тариф", "12000"),
            "/api/materials/material-1?chunkId=material-1%3A0&chunkIndex=0",
            0,
            null,
            "direct-text",
            false,
            0.1d,
            1.0d
        );
        return new MaterialRetrievalResult(
            1,
            1,
            1,
            List.of(new RetrievedMaterialChunk(contextText, source))
        );
    }
}
