import { useEffect, useMemo, useState } from "react";
import { apiClient } from "@/api/client";
import type {
  LlmProviderConfigResponse,
  LlmProviderInput,
  LlmProviderModelInfo,
  LlmProviderProbeResult,
  LlmProviderPurpose,
} from "@/types";

export type LlmProviderFormState = {
  name: string;
  providerType: "OPENAI_COMPATIBLE";
  purpose: LlmProviderPurpose;
  baseUrl: string;
  apiKey: string;
  clearApiKey: boolean;
  authHeaderName: string;
  authScheme: string;
  chatCompletionsPath: string;
  modelsPath: string;
  embeddingsPath: string;
  defaultModel: string;
  embeddingModel: string;
  temperature: string;
  timeoutSeconds: string;
  expectedEmbeddingDimension: string;
  activeChat: boolean;
  activeEmbedding: boolean;
};

export const defaultLlmProviderForm = (): LlmProviderFormState => ({
  name: "",
  providerType: "OPENAI_COMPATIBLE",
  purpose: "CHAT_AND_EMBEDDING",
  baseUrl: "",
  apiKey: "",
  clearApiKey: false,
  authHeaderName: "Authorization",
  authScheme: "Bearer",
  chatCompletionsPath: "/v1/chat/completions",
  modelsPath: "/v1/models",
  embeddingsPath: "/v1/embeddings",
  defaultModel: "",
  embeddingModel: "",
  temperature: "0.2",
  timeoutSeconds: "600",
  expectedEmbeddingDimension: "",
  activeChat: false,
  activeEmbedding: false,
});

const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const toForm = (provider: LlmProviderConfigResponse): LlmProviderFormState => ({
  name: provider.name ?? "",
  providerType: "OPENAI_COMPATIBLE",
  purpose: provider.purpose ?? "CHAT_AND_EMBEDDING",
  baseUrl: provider.baseUrl ?? "",
  apiKey: "",
  clearApiKey: false,
  authHeaderName: provider.authHeaderName ?? "Authorization",
  authScheme: provider.authScheme ?? "Bearer",
  chatCompletionsPath: provider.chatCompletionsPath ?? "/v1/chat/completions",
  modelsPath: provider.modelsPath ?? "/v1/models",
  embeddingsPath: provider.embeddingsPath ?? "/v1/embeddings",
  defaultModel: provider.defaultModel ?? "",
  embeddingModel: provider.embeddingModel ?? "",
  temperature: String(provider.temperature ?? 0.2),
  timeoutSeconds: String(provider.timeoutSeconds ?? 600),
  expectedEmbeddingDimension: provider.expectedEmbeddingDimension ? String(provider.expectedEmbeddingDimension) : "",
  activeChat: false,
  activeEmbedding: false,
});

const numberOrNull = (value: string) => {
  if (!value.trim()) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const isHttpUrl = (value: string) => {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
};

const isProviderPath = (value: string) => value.startsWith("/") && !value.includes("://");
const isAuthHeaderName = (value: string) => /^[A-Za-z0-9-]+$/.test(value);
const hasLineBreak = (value: string) => /[\r\n]/.test(value);

const validateNumber = (
  value: string,
  label: string,
  min: number,
  max?: number,
  integerOnly = false,
) => {
  if (!value.trim()) {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < min || (max != null && parsed > max)) {
    return `${label}: укажите число${max == null ? ` от ${min}` : ` от ${min} до ${max}`}.`;
  }
  if (integerOnly && !Number.isInteger(parsed)) {
    return `${label}: укажите целое число.`;
  }
  return null;
};

const validateForm = (form: LlmProviderFormState) => {
  if (!form.name.trim() || !form.baseUrl.trim()) {
    return "Название и Base URL обязательны.";
  }
  if (!isHttpUrl(form.baseUrl.trim())) {
    return "Base URL должен быть корректным http:// или https:// URL.";
  }
  const invalidPath = [
    ["Models path", form.modelsPath],
    ["Chat completions path", form.chatCompletionsPath],
    ["Embeddings path", form.embeddingsPath],
  ].find(([, value]) => !isProviderPath(String(value).trim()));
  if (invalidPath) {
    return `${invalidPath[0]} должен начинаться с "/" и не быть абсолютным URL.`;
  }
  if (!isAuthHeaderName(form.authHeaderName.trim() || "Authorization")) {
    return "Auth header name должен быть корректным HTTP header token.";
  }
  if (hasLineBreak(form.authScheme)) {
    return "Auth scheme не должен содержать переносы строк.";
  }
  if (form.clearApiKey && form.apiKey.trim()) {
    return "Нельзя одновременно очистить API key и передать новый ключ.";
  }
  return validateNumber(form.temperature, "Temperature", 0, 2)
    ?? validateNumber(form.timeoutSeconds, "Timeout seconds", 1, 3600, true)
    ?? validateNumber(form.expectedEmbeddingDimension, "Expected embedding dimension", 1, undefined, true);
};

const confirmEmbeddingReindex = () =>
  window.confirm("Смена embedding provider отправит активные материалы на повторную индексацию. Продолжить?");

export const useLlmProviders = () => {
  const [providers, setProviders] = useState<LlmProviderConfigResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<LlmProviderConfigResponse | null>(null);
  const [form, setForm] = useState<LlmProviderFormState>(() => defaultLlmProviderForm());
  const [probeResults, setProbeResults] = useState<Record<string, LlmProviderProbeResult>>({});
  const [modelResults, setModelResults] = useState<Record<string, LlmProviderModelInfo[]>>({});

  const activeChatProvider = useMemo(
    () => providers.find((provider) => provider.activeChat) ?? null,
    [providers],
  );
  const activeEmbeddingProvider = useMemo(
    () => providers.find((provider) => provider.activeEmbedding) ?? null,
    [providers],
  );

  const loadProviders = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchLlmProviders(signal);
      setProviders(payload);
      setError(null);
    } catch (loadError) {
      if (signal?.aborted) {
        return;
      }
      setError(getErrorMessage(loadError, "Не удалось загрузить LLM подключения"));
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  };

  useEffect(() => {
    const controller = new AbortController();
    void loadProviders(controller.signal);
    return () => controller.abort();
  }, []);

  const openCreateDialog = () => {
    setEditingProvider(null);
    setForm(defaultLlmProviderForm());
    setDialogOpen(true);
  };

  const openEditDialog = (provider: LlmProviderConfigResponse) => {
    setEditingProvider(provider);
    setForm(toForm(provider));
    setDialogOpen(true);
  };

  const updateForm = <K extends keyof LlmProviderFormState>(key: K, value: LlmProviderFormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const buildInput = (): LlmProviderInput => ({
    name: form.name.trim(),
    providerType: form.providerType,
    purpose: form.purpose,
    baseUrl: form.baseUrl.trim(),
    apiKey: form.clearApiKey ? null : form.apiKey.trim() ? form.apiKey.trim() : null,
    clearApiKey: editingProvider ? form.clearApiKey : false,
    authHeaderName: form.authHeaderName.trim() || "Authorization",
    authScheme: form.authScheme.trim() || "Bearer",
    chatCompletionsPath: form.chatCompletionsPath.trim() || "/v1/chat/completions",
    modelsPath: form.modelsPath.trim() || "/v1/models",
    embeddingsPath: form.embeddingsPath.trim() || "/v1/embeddings",
    defaultModel: form.defaultModel.trim() || null,
    embeddingModel: form.embeddingModel.trim() || null,
    temperature: numberOrNull(form.temperature),
    timeoutSeconds: numberOrNull(form.timeoutSeconds),
    expectedEmbeddingDimension: numberOrNull(form.expectedEmbeddingDimension),
    activeChat: !editingProvider && form.activeChat,
    activeEmbedding: !editingProvider && form.activeEmbedding,
  });

  const saveProvider = async () => {
    const validationError = validateForm(form);
    if (validationError) {
      setError(validationError);
      return;
    }
    setIsMutating(true);
    setError(null);
    try {
      const input = buildInput();
      if (editingProvider?.id) {
        await apiClient.updateLlmProvider(editingProvider.id, input);
        setMessage("LLM подключение обновлено.");
      } else {
        await apiClient.createLlmProvider(input);
        setMessage("LLM подключение создано.");
      }
      setDialogOpen(false);
      await loadProviders();
    } catch (saveError) {
      setError(getErrorMessage(saveError, "Не удалось сохранить LLM подключение"));
    } finally {
      setIsMutating(false);
    }
  };

  const probeProvider = async (provider: LlmProviderConfigResponse) => {
    if (!provider.id) {
      return;
    }
    setIsMutating(true);
    setError(null);
    try {
      const result = await apiClient.probeLlmProvider(provider.id);
      setProbeResults((current) => ({ ...current, [provider.id ?? ""]: result }));
      setMessage(result.status === "UP"
        ? "Подключение доступно."
        : result.status === "DEGRADED"
          ? "Models endpoint недоступен, chat/embeddings OK."
          : result.errorMessage ?? "Подключение недоступно.");
      await loadProviders();
    } catch (probeError) {
      setError(getErrorMessage(probeError, "Не удалось проверить подключение"));
    } finally {
      setIsMutating(false);
    }
  };

  const fetchProviderModels = async (provider: LlmProviderConfigResponse) => {
    if (!provider.id) {
      return;
    }
    setIsMutating(true);
    setError(null);
    try {
      const models = await apiClient.fetchLlmProviderModels(provider.id);
      setModelResults((current) => ({ ...current, [provider.id ?? ""]: models }));
      setMessage(models.length > 0 ? `Получено моделей: ${models.length}` : "Endpoint не вернул модели.");
    } catch (modelsError) {
      setError(getErrorMessage(modelsError, "Не удалось получить список моделей"));
    } finally {
      setIsMutating(false);
    }
  };

  const activateProvider = async (provider: LlmProviderConfigResponse, purpose: "CHAT" | "EMBEDDING") => {
    if (!provider.id) {
      return;
    }
    if (purpose === "EMBEDDING" && !confirmEmbeddingReindex()) {
      return;
    }
    setIsMutating(true);
    setError(null);
    try {
      await apiClient.activateLlmProvider(provider.id, { purpose });
      setMessage(purpose === "CHAT" ? "Chat provider активирован." : "Embedding provider активирован.");
      await loadProviders();
    } catch (activateError) {
      setError(getErrorMessage(activateError, "Не удалось активировать provider"));
    } finally {
      setIsMutating(false);
    }
  };

  const activateFallback = async (purpose: "CHAT" | "EMBEDDING") => {
    if (purpose === "EMBEDDING" && !confirmEmbeddingReindex()) {
      return;
    }
    setIsMutating(true);
    setError(null);
    try {
      await apiClient.activateLlmFallback({ purpose });
      setMessage(purpose === "CHAT" ? "Chat переключен на env fallback." : "Embeddings переключены на env fallback.");
      await loadProviders();
    } catch (fallbackError) {
      setError(getErrorMessage(fallbackError, "Не удалось переключиться на fallback"));
    } finally {
      setIsMutating(false);
    }
  };

  const deleteProvider = async (provider: LlmProviderConfigResponse) => {
    if (!provider.id) {
      return;
    }
    setIsMutating(true);
    setError(null);
    try {
      await apiClient.deleteLlmProvider(provider.id);
      setMessage("LLM подключение удалено.");
      await loadProviders();
    } catch (deleteError) {
      setError(getErrorMessage(deleteError, "Не удалось удалить LLM подключение"));
    } finally {
      setIsMutating(false);
    }
  };

  return {
    activeChatProvider,
    activeEmbeddingProvider,
    activateFallback,
    activateProvider,
    deleteProvider,
    dialogOpen,
    editingProvider,
    error,
    fetchProviderModels,
    form,
    isLoading,
    isMutating,
    loadProviders,
    message,
    modelResults,
    openCreateDialog,
    openEditDialog,
    probeProvider,
    probeResults,
    providers,
    saveProvider,
    setDialogOpen,
    updateForm,
  };
};
