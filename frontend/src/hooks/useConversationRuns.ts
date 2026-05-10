import { useCallback, useEffect, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { ConversationRunDetail } from "../types";

type UseConversationRunsOptions = {
  enabled?: boolean;
};

export const useConversationRuns = (
  conversationId: string | null,
  { enabled = true }: UseConversationRunsOptions = {},
) => {
  const [runs, setRuns] = useState<ConversationRunDetail[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const loadedConversationRef = useRef<string | null>(null);

  const loadRunsFor = useCallback(async (
    targetConversationId: string | null,
    signal?: AbortSignal,
    options: { force?: boolean } = {},
  ) => {
    if (!targetConversationId) {
      setRuns([]);
      loadedConversationRef.current = null;
      return null;
    }
    if (!enabled && !options.force) {
      return null;
    }

    setIsLoading(true);
    try {
      const payload = await apiClient.fetchConversationRuns(targetConversationId, signal);
      setRuns(payload);
      setError(null);
      loadedConversationRef.current = targetConversationId;
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setError(translateCommonApiError(loadError, "Не удалось загрузить ветку беседы"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    if (loadedConversationRef.current === conversationId) {
      return;
    }

    const controller = new AbortController();
    void loadRunsFor(conversationId, controller.signal);
    return () => controller.abort();
  }, [conversationId, enabled, loadRunsFor]);

  useEffect(() => {
    if (enabled) {
      return;
    }
    setRuns([]);
    setError(null);
    setIsLoading(false);
    loadedConversationRef.current = null;
  }, [enabled]);

  return {
    runs,
    error,
    isLoading,
    loadRuns: (signal?: AbortSignal) => loadRunsFor(conversationId, signal, { force: enabled }),
    loadRunsFor: (targetConversationId: string, signal?: AbortSignal) =>
      loadRunsFor(targetConversationId, signal, { force: enabled }),
  };
};
