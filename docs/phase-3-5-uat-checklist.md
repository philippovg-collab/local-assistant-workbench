# Phase 3–5 UAT Checklist

## Phase 3 — Explainability и deep-link к источнику
- Открыть `RAG Studio` и задать вопрос, который точно возвращает 1-2 источника.
- Нажать `Открыть источник` у каждого source card.
- Проверить, что dialog открывает нужный материал, показывает `Jump target`, а нужный chunk помечен `retrieval target`.
- Проверить, что page/chunk badge совпадает с карточкой источника в ответе.

## Phase 4 — Answer modes
- В `strict_sources_only` задать вопрос без подтверждения в документах.
- Проверить, что ответ равен `Не найдено в источниках.`
- В `documents_only` задать вопрос, где модель может расширить вывод сверх найденного факта.
- Проверить, что система не выпускает неподтверждённый вывод.
- В `broader_reasoning` повторить такой же сценарий.
- Проверить, что расширение явно помечено блоком `Более широкое рассуждение:`.

## Phase 5 — Audit trail и compare
- Выполнить минимум 2 похожих запроса с разными `answerMode`, corpus scope или инструкциями.
- Открыть `Audit Trail` и выбрать один запуск как `База`, второй как `Сравнить`.
- Проверить, что compare показывает отличия по model, answer mode, scope, preset revisions и chunk ids.
- Открыть оба inspectors и проверить, что видны `Prompt`, `Answer`, `Scope`, `Retrieval`, `Support`, `Instruction stack` и `Sources`.
