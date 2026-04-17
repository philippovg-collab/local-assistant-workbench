import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { ModelInfo } from "../types";

const POLL_INTERVAL_MS = 15_000;
const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;
const isPageVisible = () => typeof document === "undefined" || document.visibilityState !== "hidden";

export const useModels = () => {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let controller: AbortController | null = null;

    const load = async (signal?: AbortSignal) => {
      try {
        const payload = await apiClient.fetchModels(signal);
        setModels(payload);
        setError(null);
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

    tick();
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
  }, []);

  return { models, error };
};
