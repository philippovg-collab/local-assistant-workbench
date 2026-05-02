import { useEffect, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { ChatAuditRunDetail, ChatAuditRunSummary } from "../types";

type UseChatRunsOptions = {
  enabled?: boolean;
};

export const useChatRuns = (workspaceKey?: string | null, options: UseChatRunsOptions = {}) => {
  const { enabled = true } = options;
  const [runs, setRuns] = useState<ChatAuditRunSummary[]>([]);
  const [selectedRun, setSelectedRun] = useState<ChatAuditRunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loadedParamsRef = useRef<string | null>(null);
  const paramsKey = workspaceKey?.trim() ?? "";

  const loadRuns = async (signal?: AbortSignal, options: { force?: boolean } = {}) => {
    if (!enabled && !options.force) {
      return null;
    }

    try {
      const payload = await apiClient.fetchChatRuns({ workspaceKey }, signal);
      setRuns(payload);
      setError(null);
      loadedParamsRef.current = paramsKey;
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setError(translateCommonApiError(loadError, "Не удалось загрузить историю запусков"));
      return null;
    }
  };

  useEffect(() => {
    if (!enabled) {
      return;
    }
    if (loadedParamsRef.current === paramsKey) {
      return;
    }

    const controller = new AbortController();
    void loadRuns(controller.signal);
    return () => controller.abort();
  }, [enabled, paramsKey, workspaceKey]);

  const loadRun = async (runId: string, signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchChatRun(runId, signal);
      setSelectedRun(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setError(translateCommonApiError(loadError, "Не удалось загрузить аудит запуска"));
      return null;
    }
  };

  return {
    runs,
    selectedRun,
    error,
    loadRuns: (signal?: AbortSignal) => loadRuns(signal, { force: true }),
    loadRun,
  };
};
