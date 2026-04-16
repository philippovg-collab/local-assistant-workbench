import { ApiClientError, isApiClientError } from "./client";

const buildUnexpectedErrorMessage = (error: ApiClientError) =>
  error.requestId
    ? `Сервер вернул непредвиденную ошибку. requestId: ${error.requestId}.`
    : "Сервер вернул непредвиденную ошибку.";

export const translateCommonApiError = (error: unknown, fallback: string) => {
  if (isApiClientError(error)) {
    switch (error.code) {
      case "internal.unexpected_error":
        return buildUnexpectedErrorMessage(error);
      case "llm.provider_unavailable":
        return "Не удалось связаться с локальной LLM. Проверь, что Ollama запущен и доступен.";
      case "llm.provider_bad_response":
        return "Локальная LLM вернула некорректный ответ. Повтори запрос чуть позже.";
      case "llm.provider_parse_failed":
        return "Не удалось разобрать ответ локальной LLM. Повтори запрос.";
      case "llm.provider_empty_choices":
        return "Локальная LLM вернула пустой результат. Попробуй переформулировать запрос.";
      case "llm.provider_interrupted":
        return "Запрос к локальной LLM был прерван. Попробуй снова.";
      case "llm.invalid_configuration":
        return "Конфигурация локальной LLM выглядит некорректной. Проверь настройки backend.";
      case "request.invalid_payload":
        return "Запрос заполнен некорректно. Обнови форму и попробуй ещё раз.";
      default:
        return error.message || fallback;
    }
  }

  return error instanceof Error ? error.message : fallback;
};
