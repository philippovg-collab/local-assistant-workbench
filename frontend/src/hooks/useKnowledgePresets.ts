import { useEffect, useRef, useState } from "react";
import { apiClient, isApiClientError } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  CreateKnowledgePresetRequest,
  KnowledgePresetDetail,
  KnowledgePresetRevisionDiff,
  KnowledgePresetRevisionDetail,
  KnowledgePresetSummary,
} from "../types";

const translatePresetError = (error: unknown, fallback: string) => {
  if (isApiClientError(error)) {
    if (error.code?.startsWith("knowledge_preset.storage_") || error.code?.startsWith("knowledge_preset.")) {
      return translateCommonApiError(error, fallback);
    }
  }
  return translateCommonApiError(error, fallback);
};

type UseKnowledgePresetsOptions = {
  enabled?: boolean;
};

export const useKnowledgePresets = (options: UseKnowledgePresetsOptions = {}) => {
  const { enabled = true } = options;
  const [presets, setPresets] = useState<KnowledgePresetSummary[]>([]);
  const [selectedPreset, setSelectedPreset] = useState<KnowledgePresetDetail | null>(null);
  const [revisions, setRevisions] = useState<KnowledgePresetRevisionDetail[]>([]);
  const [revisionDiff, setRevisionDiff] = useState<KnowledgePresetRevisionDiff | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(enabled);
  const hasLoadedRef = useRef(false);

  const loadPresets = async (signal?: AbortSignal) => {
    if (!enabled) {
      setIsLoading(false);
      return null;
    }

    setIsLoading(true);
    try {
      const payload = await apiClient.fetchKnowledgePresets(signal);
      setPresets(payload);
      setRevisionDiff((current) =>
        selectedPreset && payload.some((preset) => preset.id === selectedPreset.id) ? current : null,
      );
      setError(null);
      hasLoadedRef.current = true;
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setError(translatePresetError(loadError, "Не удалось загрузить knowledge presets"));
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!enabled) {
      setIsLoading(false);
      return;
    }
    if (hasLoadedRef.current) {
      return;
    }

    const controller = new AbortController();
    void loadPresets(controller.signal);
    return () => controller.abort();
  }, [enabled]);

  const loadPreset = async (presetId: string, signal?: AbortSignal) => {
    try {
      const detail = await apiClient.fetchKnowledgePreset(presetId, signal);
      setSelectedPreset(detail);
      return detail;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setActionError(translatePresetError(loadError, "Не удалось загрузить knowledge preset"));
      return null;
    }
  };

  const loadRevisions = async (presetId: string, signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchKnowledgePresetRevisions(presetId, signal);
      setRevisions(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setActionError(translatePresetError(loadError, "Не удалось загрузить историю knowledge preset"));
      return null;
    }
  };

  const createPreset = async (input: CreateKnowledgePresetRequest) => {
    setActionError(null);
    setMessage(null);
    try {
      const created = await apiClient.createKnowledgePreset(input);
      await loadPresets();
      setSelectedPreset(created);
      setRevisionDiff(null);
      setMessage("Knowledge preset сохранён.");
      return created;
    } catch (submissionError) {
      setActionError(translatePresetError(submissionError, "Не удалось сохранить knowledge preset"));
      throw submissionError;
    }
  };

  const updatePreset = async (presetId: string, input: CreateKnowledgePresetRequest) => {
    setActionError(null);
    setMessage(null);
    try {
      const updated = await apiClient.updateKnowledgePreset(presetId, input);
      await loadPresets();
      setSelectedPreset(updated);
      setRevisionDiff(null);
      setMessage("Knowledge preset обновлён.");
      return updated;
    } catch (submissionError) {
      setActionError(translatePresetError(submissionError, "Не удалось обновить knowledge preset"));
      throw submissionError;
    }
  };

  const restoreRevision = async (presetId: string, revision: number) => {
    setActionError(null);
    setMessage(null);
    try {
      const restored = await apiClient.restoreKnowledgePresetRevision(presetId, revision);
      await Promise.all([loadPresets(), loadRevisions(presetId)]);
      setSelectedPreset(restored);
      setRevisionDiff(null);
      setMessage(`Knowledge preset восстановлен из ревизии ${revision}.`);
      return restored;
    } catch (submissionError) {
      setActionError(translatePresetError(submissionError, "Не удалось восстановить knowledge preset"));
      throw submissionError;
    }
  };

  const deletePreset = async (presetId: string) => {
    setActionError(null);
    setMessage(null);
    try {
      await apiClient.deleteKnowledgePreset(presetId);
      await loadPresets();
      setSelectedPreset((current) => (current?.id === presetId ? null : current));
      setRevisionDiff((current) => (selectedPreset?.id === presetId ? null : current));
      setRevisions((current) => (selectedPreset?.id === presetId ? [] : current));
      setMessage("Knowledge preset удалён.");
    } catch (submissionError) {
      setActionError(translatePresetError(submissionError, "Не удалось удалить knowledge preset"));
      throw submissionError;
    }
  };

  const loadRevisionDiff = async (
    presetId: string,
    fromRevision: number,
    toRevision: number,
    signal?: AbortSignal,
  ) => {
    try {
      const payload = await apiClient.fetchKnowledgePresetDiff(presetId, fromRevision, toRevision, signal);
      setRevisionDiff(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setActionError(translatePresetError(loadError, "Не удалось загрузить diff knowledge preset"));
      return null;
    }
  };

  return {
    presets,
    selectedPreset,
    revisions,
    revisionDiff,
    error,
    actionError,
    message,
    isLoading,
    loadPresets,
    loadPreset,
    loadRevisions,
    loadRevisionDiff,
    createPreset,
    updatePreset,
    restoreRevision,
    deletePreset,
  };
};
