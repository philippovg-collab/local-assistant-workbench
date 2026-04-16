# Local Assistant Workbench

Локальная студия для работы с `ollama` как с единым assistant-workbench, а не как с набором несвязанных фич.

Продуктовая модель теперь одна:

- единый backend-контракт для чата через `POST /api/chat`
- два режима исполнения: `RAG` и `DIRECT`
- одна prompt policy для модели, system prompt и выбранных инструкций
- локальное file-based хранилище материалов и инструкций с изоляцией битых записей

## Что уже есть

- локальный runtime `ollama`
- веб-интерфейс на `React + Vite`
- backend на `Spring Boot`
- единый chat execution pipeline для direct и RAG сценариев
- библиотека инструкций, которая участвует в runtime только если пользователь явно её выбрал
- ingestion материалов при записи: нормализация, дедупликация, chunking и индексирование

## Продуктовая модель

Приложение остается локальным assistant-workbench с двумя режимами:

- `RAG`: ответ строится только по найденному контексту из загруженных материалов
- `DIRECT`: прямой запрос к локальной модели без retrieval

Оба режима идут через один API и один LLM client.

## Prompt Policy

Правила сборки runtime prompt фиксированы и одинаковы для всех клиентов:

1. `request.model` переопределяет модель по умолчанию из backend-конфига.
2. `request.systemPrompt` переопределяет системный prompt по умолчанию из backend-конфига.
3. `instructionIds` подтягиваются из библиотеки инструкций и добавляются в system prompt в указанном порядке.
4. Для `RAG` backend всегда добавляет grounding rules: отвечать только по найденному контексту, явно признавать неполноту контекста и не галлюцинировать.

Если инструкция не выбрана, она не влияет на выполнение запроса.

## Быстрый старт

1. Запусти `ollama`, backend и frontend:

```bash
./scripts/start-studio.sh
```

2. Открой интерфейс:

```text
http://127.0.0.1:5173
```

3. Добавь материалы, выбери инструкции при необходимости и используй либо RAG-панель, либо direct playground.

## Отдельный запуск по частям

```bash
./scripts/start-ollama.sh
./scripts/pull-model.sh qwen2.5:7b
./scripts/start-backend.sh
./scripts/start-frontend.sh
```

Для backend startup и backend tests проект теперь ожидает `Java 21`. Скрипты сначала пробуют `JAVA_21_HOME`, затем текущий `JAVA_HOME`, Homebrew `openjdk@21` и только потом системный `java_home -v 21`.

## Остановка

```bash
./scripts/stop-studio.sh
./scripts/stop-ollama.sh
```

## Полезные команды

```bash
./scripts/chat.sh
./scripts/chat.sh qwen2.5:7b
./scripts/test-model.sh
./scripts/test-backend.sh
curl http://127.0.0.1:11434/api/tags
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8080/api/models
curl http://127.0.0.1:8080/api/materials
curl http://127.0.0.1:8080/api/materials/policy
curl http://127.0.0.1:8080/api/instructions
```

## Основной Chat API

```bash
POST http://127.0.0.1:8080/api/chat
Content-Type: application/json
```

Тело запроса:

```json
{
  "mode": "RAG",
  "model": "qwen2.5:7b",
  "prompt": "Какие условия у тарифа Премиум?",
  "systemPrompt": "Отвечай кратко и по делу.",
  "instructionIds": [
    "3f4bdf02-02f9-4721-b658-e75f62333121"
  ]
}
```

Пример ответа:

```json
{
  "mode": "RAG",
  "model": "qwen2.5:7b",
  "prompt": "Какие условия у тарифа Премиум?",
  "answer": "Тариф Премиум стоит 12000 тенге в месяц и включает приоритетную поддержку.",
  "createdAt": "2026-04-16T09:15:00Z",
  "promptTokens": 182,
  "completionTokens": 31,
  "totalTokens": 213,
  "appliedInstructions": [
    {
      "id": "3f4bdf02-02f9-4721-b658-e75f62333121",
      "title": "Базовая роль ассистента",
      "category": "system"
    }
  ],
  "sources": [
    {
      "materialId": "c6dd6caa-ed4c-4135-8f03-e67d3fbdc4de",
      "title": "Pricing note",
      "excerpt": "Тариф Премиум стоит 12000 тенге в месяц и включает приоритетную поддержку.",
      "score": 10,
      "page": 1,
      "extractor": "pdfbox",
      "ocrUsed": false
    }
  ]
}
```

### Пример `RAG`

```bash
curl http://127.0.0.1:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "RAG",
    "model": "qwen2.5:7b",
    "prompt": "Какие условия у тарифа Премиум?"
  }'
```

Если релевантный контекст не найден, backend вернет понятный ответ и пустой `sources`.

### Пример `DIRECT`

```bash
curl http://127.0.0.1:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "DIRECT",
    "model": "qwen2.5:7b",
    "systemPrompt": "Отвечай понятно и кратко",
    "prompt": "Объясни, как работает request-response для локальной LLM"
  }'
```

Для `DIRECT` режимa `sources` будет пустым, а выбранные инструкции вернутся в `appliedInstructions`.

## API моделей

```bash
GET /api/models
```

Backend проксирует список локально доступных моделей из `ollama`.

## API материалов

```bash
GET    /api/materials
GET    /api/materials/policy
POST   /api/materials
POST   /api/materials/upload
DELETE /api/materials/{id}
```

### Политика upload

`GET /api/materials/policy` возвращает актуальный backend-контракт для загрузки:

```json
{
  "maxUploadBytes": 2000000,
  "acceptedExtensions": [
    "txt",
    "md",
    "markdown",
    "csv",
    "json",
    "xml",
    "yaml",
    "yml",
    "log",
    "sql",
    "java",
    "kt",
    "js",
    "ts",
    "tsx",
    "jsx",
    "py",
    "properties",
    "html",
    "htm",
    "doc",
    "docx",
    "rtf",
    "odt",
    "pdf"
  ],
  "acceptedMimeHints": [
    "text/plain",
    "text/markdown",
    "text/csv",
    "application/json",
    "application/xml",
    "text/xml",
    "application/x-yaml",
    "text/yaml",
    "text/html",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/rtf",
    "text/rtf",
    "application/vnd.oasis.opendocument.text",
    "application/pdf"
  ],
  "richDocumentSupport": true,
  "pdf": {
    "enabled": true,
    "scannedPdfSupport": true,
    "mode": "embedded_text_and_ocr",
    "ocrLanguages": ["kaz", "rus", "eng"],
    "ocrMaxPages": 12
  }
}
```

Если runtime self-check видит, что `tesseract` или нужные language packs недоступны, `pdf.enabled` остаётся `true`, но policy переключается в partial mode:

```json
{
  "pdf": {
    "enabled": true,
    "scannedPdfSupport": false,
    "mode": "embedded_text_only",
    "ocrReasonCode": "material.ocr_unavailable",
    "ocrReasonMessage": "Tesseract OCR binary is unavailable at 'tesseract'.",
    "ocrLanguages": ["kaz", "rus", "eng"],
    "ocrMaxPages": 12
  }
}
```

Frontend использует этот endpoint для preflight-проверок до отправки multipart-запроса и для helper/warning текста в upload-форме.

### Health readiness

`GET /api/health` теперь отражает не только факт запуска Spring Boot, но и OCR readiness:

```json
{
  "application": "spring-backend",
  "status": "DEGRADED",
  "timestamp": "2026-04-16T09:20:00Z",
  "ocrStatus": "DOWN",
  "ocrReasonCode": "material.ocr_unavailable",
  "ocrReasonMessage": "Tesseract OCR binary is unavailable at 'tesseract'.",
  "ocrLanguages": ["kaz", "rus", "eng"]
}
```

- `status=UP` означает, что backend готов и OCR readiness подтверждён либо OCR отключён конфигом
- `status=DEGRADED` означает, что backend поднялся, но scanned PDF должны отклоняться до rasterization
- `./scripts/start-backend.sh` считает backend готовым только при `status=UP`, чтобы startup smoke-check не пропустил broken OCR runtime

Текстовый материал:

```json
{
  "title": "Pricing note",
  "content": "Тариф Премиум стоит 12000 тенге в месяц."
}
```

### Поддерживаемые форматы upload

- `.txt`
- `.md`
- `.markdown`
- `.csv`
- `.json`
- `.xml`
- `.yaml`
- `.yml`
- `.log`
- `.sql`
- `.java`
- `.kt`
- `.js`
- `.ts`
- `.tsx`
- `.jsx`
- `.py`
- `.properties`
- `.html`
- `.htm`
- `.docx`
- `.doc`
- `.rtf`
- `.odt`
- `.pdf`

Форматы `.doc/.docx/.rtf/.odt/.html` извлекаются кроссплатформенно через JVM-native extractor. Для `.pdf` backend всегда пытается прочитать embedded text через PDFBox. Если OCR readiness подтверждён, scanned PDF тоже поддерживается через `tesseract`; если нет, policy остаётся с `.pdf`, но переключается в режим `embedded_text_only`.

### OCR и серверные зависимости

Для серверного OCR нужны:

- бинарник `tesseract`
- языковые пакеты `kaz`, `rus`, `eng`

Пример для Debian/Ubuntu:

```bash
sudo apt-get install -y tesseract-ocr tesseract-ocr-kaz tesseract-ocr-rus tesseract-ocr-eng
```

Backend кэширует OCR readiness на короткий TTL и проверяет:

- `app.ocr.enabled`
- доступность `binary-path`
- наличие всех языков из `app.ocr.languages` через `tesseract --list-langs`

На старте backend дополнительно пишет в лог результат OCR readiness check. Если PDF требует OCR, а `tesseract` или нужные language packs недоступны, policy заранее сообщает partial support, а реальный upload завершится типизированной ошибкой backend ещё до page rasterization.

## API инструкций

Инструкции не участвуют в retrieval сами по себе. Они становятся частью runtime только через `instructionIds` в chat-запросе.

```bash
GET    /api/instructions
POST   /api/instructions
DELETE /api/instructions/{id}
```

Пример:

```json
{
  "title": "Базовая роль ассистента",
  "category": "system",
  "content": "Отвечай кратко, структурно и на русском языке."
}
```

## Guardrails и устойчивость

- загрузка файлов ограничена multipart-лимитами backend
- извлечение текста из upload-файлов выполняется с timeout
- дедупликация и chunking выполняются при записи, а не на каждом chat-запросе
- RAG prompt строится из полного найденного chunk, а наружу в `sources[].excerpt` уходит только короткий preview
- поврежденные записи из file storage изолируются в quarantine и не валят весь список материалов или инструкций
- ошибки backend возвращаются в едином формате:

```json
{
  "code": "provider_unavailable",
  "message": "LLM provider is unavailable",
  "timestamp": "2026-04-16T09:20:00Z",
  "requestId": "6c0d031d-6048-4c3f-b6e0-d10d69fb0d08"
}
```

`requestId` возвращается для неожиданных `500`-ошибок и помогает быстро сопоставить UI-инцидент с backend-логами. Ошибки upload теперь нормализованы:

- `413 material.upload_too_large` для файлов выше лимита
- `400 material.invalid_multipart` для битого multipart
- `400 material.missing_file_part` если часть `file` отсутствует
- `400 material.unsupported_format` для неподдерживаемого расширения
- `400 material.extraction_failed` если rich-document не удалось распарсить
- `400 material.ocr_disabled` если PDF требует OCR, но OCR выключен
- `400 material.ocr_unavailable` если бинарник `tesseract` недоступен
- `400 material.ocr_language_data_missing` если не хватает traineddata для `kaz/rus/eng`
- `400 material.ocr_timeout` если OCR не уложился в лимит
- `400 material.ocr_page_limit_exceeded` если PDF слишком большой для OCR по текущему лимиту
- `400 material.ocr_render_budget_exceeded` если страница PDF слишком велика для безопасного rasterization перед OCR

## Низкоуровневый OpenAI-совместимый вызов напрямую в Ollama

Приложение использует свой unified API, но при необходимости можно обращаться напрямую к raw OpenAI-compatible endpoint самого `ollama`:

```bash
curl http://127.0.0.1:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen2.5:7b",
    "messages": [
      { "role": "system", "content": "Отвечай кратко и по-русски." },
      { "role": "user", "content": "Привет, кто ты?" }
    ]
  }'
```

## Где что лежит

- frontend: `frontend/`
- backend: `backend/`
- локальное хранилище backend: `backend/storage/`
- материалы: `backend/storage/materials/`
- инструкции: `backend/storage/instructions/`
- quarantine для поврежденных записей: `backend/storage/quarantine/`
- модели Ollama: `~/.ollama/models`

## Если модель тяжеловата

По умолчанию разумный старт: `qwen2.5:7b`.

Если нужен более легкий режим:

```bash
./scripts/pull-model.sh qwen2.5:3b
```
