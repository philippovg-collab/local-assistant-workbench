import { useEffect, useRef, useState } from "react";
import { apiClient } from "../api/client";
import type { ModelInfo } from "../types";

const POLL_INTERVAL_MS = 15_000;
const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;
const isPageVisible = () => typeof document === "undefined" || document.visibilityState !== "hidden";

type UseModelsOptions = {
  enabled?: boolean;
};

export const useModels = (options: UseModelsOptions = {}) => {
  const { enabled = true } = options;
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const hasLoadedRef = useRef(false);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    let controller: AbortController | null = null;

    const load = async (signal?: AbortSignal) => {
      try {
        const payload = await apiClient.fetchModels(signal);
        setModels(payload);
        setError(null);
        hasLoadedRef.current = true;
      } catch (loadError) {
        if (signal?.aborted) {
          return;
        }

        setError(getErrorMessage(loadError, "Не удалось загрузить список моделей"));
      }
    };

    const tick = () => {
      if (!isPageVisible()) {
        return;
      }

      controller?.abort();
      controller = new AbortController();
      void load(controller.signal);
    };

    if (!hasLoadedRef.current) {
      tick();
    }
    const intervalId = window.setInterval(tick, POLL_INTERVAL_MS);
    const handleVisibilityChange = () => {
      if (isPageVisible()) {
        tick();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      controller?.abort();
      window.clearInterval(intervalId);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [enabled]);

  return { models, error };
};
