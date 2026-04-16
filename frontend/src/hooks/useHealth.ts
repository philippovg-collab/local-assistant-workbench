import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { HealthResponse } from "../types";

const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

export const useHealth = () => {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      try {
        const payload = await apiClient.fetchHealth(controller.signal);
        setHealth(payload);
        setError(null);
      } catch (loadError) {
        if (controller.signal.aborted) {
          return;
        }

        setError(getErrorMessage(loadError, "Не удалось связаться с backend"));
      }
    };

    void load();
    return () => controller.abort();
  }, []);

  return { health, error };
};
