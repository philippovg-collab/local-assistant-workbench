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
      case "embedding.provider_unavailable":
        return "Не удалось связаться с локальным embedding runtime. Проверь Ollama и модель embeddings.";
      case "embedding.provider_bad_response":
      case "embedding.provider_parse_failed":
      case "embedding.provider_empty_embedding":
      case "embedding.provider_dimension_mismatch":
      case "embedding.provider_interrupted":
      case "embedding.invalid_configuration":
        return "Embedding runtime backend сейчас работает некорректно. Проверь readiness и повтори попытку позже.";
      case "chat.invalid_request":
      case "chat.invalid_prompt":
      case "request.invalid_payload":
        return "Запрос заполнен некорректно. Обнови форму и попробуй ещё раз.";
      case "request.field_too_large":
      case "request.too_many_items":
      case "request.payload_too_large":
        return "Запрос слишком большой. Сократи текст или количество выбранных значений.";
      case "auth.unauthenticated":
        return "Сессия не активна. Войди и повтори действие.";
      case "auth.invalid_credentials":
        return "Логин или пароль не подходят.";
      case "auth.forbidden":
        return "Для этого действия недостаточно прав.";
      case "instruction.inactive":
        return "Выбранная инструкция сейчас неактивна и не может участвовать в запросе.";
      case "knowledge_preset.inactive":
        return "Выбранный knowledge preset сейчас неактивен и не может ограничивать корпус.";
      default:
        return error.message || fallback;
    }
  }

  return error instanceof Error ? error.message : fallback;
};
