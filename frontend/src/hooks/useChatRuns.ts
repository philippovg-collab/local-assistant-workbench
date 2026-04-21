import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { ChatAuditRunDetail, ChatAuditRunSummary } from "../types";

export const useChatRuns = (workspaceKey?: string | null) => {
  const [runs, setRuns] = useState<ChatAuditRunSummary[]>([]);
  const [selectedRun, setSelectedRun] = useState<ChatAuditRunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadRuns = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchChatRuns({ workspaceKey }, signal);
      setRuns(payload);
      setError(null);
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
    const controller = new AbortController();
    void loadRuns(controller.signal);
    return () => controller.abort();
  }, [workspaceKey]);

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
    loadRuns,
    loadRun,
  };
};
