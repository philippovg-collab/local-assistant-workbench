import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { ModelInfo } from "../types";

const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

export const useModels = () => {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      try {
        const payload = await apiClient.fetchModels(controller.signal);
        setModels(payload);
        setError(null);
      } catch (loadError) {
        if (controller.signal.aborted) {
          return;
        }

        setError(getErrorMessage(loadError, "Не удалось загрузить список моделей"));
      }
    };

    void load();
    return () => controller.abort();
  }, []);

  return { models, error };
};
