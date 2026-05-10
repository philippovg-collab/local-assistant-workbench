package com.example.demo.service;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatRagNoContextPolicy {

    public NoContextCompletion evaluate(
        MaterialRetrievalResult retrievalResult,
        AnswerMode answerMode
    ) {
        if (retrievalResult.materialCount() == 0) {
            return new NoContextCompletion(
                "Сначала добавьте материалы. Без локального контекста RAG-режим не сможет ответить.",
                List.of(),
                false
            );
        }
        if (retrievalResult.activeMaterialCount() == 0) {
            return new NoContextCompletion(
                "В базе знаний остались только архивные версии материалов. Добавьте новую активную версию или восстановите предыдущую.",
                List.of(),
                false
            );
        }
        if (retrievalResult.scopedMaterialCount() == 0) {
            return new NoContextCompletion(
                "В выбранном наборе знаний нет материалов. Измени пресет или загрузи документы в этот корпус.",
                List.of(),
                false
            );
        }
        if (retrievalResult.scopedReadyMaterialCount() == 0) {
            return new NoContextCompletion(
                "В выбранном корпусе есть материалы, но индекс ещё не готов. Дождитесь завершения индексации и повторите запрос.",
                List.of(),
                false
            );
        }
        if (retrievalResult.sources().isEmpty()) {
            return new NoContextCompletion(
                answerMode == AnswerMode.STRICT_SOURCES_ONLY
                    ? "Не найдено в источниках."
                    : "Не нашёл релевантных фрагментов в загруженных материалах. Уточните запрос или обновите материалы.",
                List.of(),
                answerMode == AnswerMode.STRICT_SOURCES_ONLY
            );
        }
        if (answerMode == AnswerMode.STRICT_SOURCES_ONLY
            && !"sufficient".equalsIgnoreCase(retrievalResult.retrievalTrace().supportVerdict())) {
            return new NoContextCompletion(
                "Не найдено в источниках.",
                retrievalResult.sources(),
                true
            );
        }
        return null;
    }

    public record NoContextCompletion(
        String answer,
        List<ChatSource> sources,
        boolean strictSourcesBlockedAnswer
    ) {
    }
}
