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

const translateFacetError = (error: unknown, fallback: string) => {
  if (isApiClientError(error)) {
    if (error.code?.startsWith("knowledge_preset.storage_") || error.code?.startsWith("knowledge_preset.")) {
      return translateCommonApiError(error, fallback);
    }
  }
  return translateCommonApiError(error, fallback);
};

type UseKnowledgeFacetsOptions = {
  enabled?: boolean;
};

export const useKnowledgeFacets = (options: UseKnowledgeFacetsOptions = {}) => {
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
      const payload = await apiClient.fetchKnowledgeFacets(signal);
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
      setError(translateFacetError(loadError, "Не удалось загрузить фасеты"));
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
      const detail = await apiClient.fetchKnowledgeFacet(presetId, signal);
      setSelectedPreset(detail);
      return detail;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setActionError(translateFacetError(loadError, "Не удалось загрузить фасет"));
      return null;
    }
  };

  const loadRevisions = async (presetId: string, signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchKnowledgeFacetRevisions(presetId, signal);
      setRevisions(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setActionError(translateFacetError(loadError, "Не удалось загрузить историю фасета"));
      return null;
    }
  };

  const createPreset = async (input: CreateKnowledgePresetRequest) => {
    setActionError(null);
    setMessage(null);
    try {
      const created = await apiClient.createKnowledgeFacet(input);
      await loadPresets();
      setSelectedPreset(created);
      setRevisionDiff(null);
      setMessage("Фасет сохранён.");
      return created;
    } catch (submissionError) {
      setActionError(translateFacetError(submissionError, "Не удалось сохранить фасет"));
      throw submissionError;
    }
  };

  const updatePreset = async (presetId: string, input: CreateKnowledgePresetRequest) => {
    setActionError(null);
    setMessage(null);
    try {
      const updated = await apiClient.updateKnowledgeFacet(presetId, input);
      await loadPresets();
      setSelectedPreset(updated);
      setRevisionDiff(null);
      setMessage("Фасет обновлён.");
      return updated;
    } catch (submissionError) {
      setActionError(translateFacetError(submissionError, "Не удалось обновить фасет"));
      throw submissionError;
    }
  };

  const restoreRevision = async (presetId: string, revision: number) => {
    setActionError(null);
    setMessage(null);
    try {
      const restored = await apiClient.restoreKnowledgeFacetRevision(presetId, revision);
      await Promise.all([loadPresets(), loadRevisions(presetId)]);
      setSelectedPreset(restored);
      setRevisionDiff(null);
      setMessage(`Фасет восстановлен из ревизии ${revision}.`);
      return restored;
    } catch (submissionError) {
      setActionError(translateFacetError(submissionError, "Не удалось восстановить фасет"));
      throw submissionError;
    }
  };

  const deletePreset = async (presetId: string) => {
    setActionError(null);
    setMessage(null);
    try {
      await apiClient.deleteKnowledgeFacet(presetId);
      await loadPresets();
      setSelectedPreset((current) => (current?.id === presetId ? null : current));
      setRevisionDiff((current) => (selectedPreset?.id === presetId ? null : current));
      setRevisions((current) => (selectedPreset?.id === presetId ? [] : current));
      setMessage("Фасет удалён.");
    } catch (submissionError) {
      setActionError(translateFacetError(submissionError, "Не удалось удалить фасет"));
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
      const payload = await apiClient.fetchKnowledgeFacetDiff(presetId, fromRevision, toRevision, signal);
      setRevisionDiff(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setActionError(translateFacetError(loadError, "Не удалось загрузить diff фасета"));
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
