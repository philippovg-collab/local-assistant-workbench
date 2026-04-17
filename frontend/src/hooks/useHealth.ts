import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { HealthResponse } from "../types";

const POLL_INTERVAL_MS = 15_000;
const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;
const isPageVisible = () => typeof document === "undefined" || document.visibilityState !== "hidden";

export const useHealth = () => {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let controller: AbortController | null = null;

    const load = async (signal?: AbortSignal) => {
      try {
        const payload = await apiClient.fetchHealth(signal);
        setHealth(payload);
        setError(null);
      } catch (loadError) {
        if (signal?.aborted) {
          return;
        }

        setError(getErrorMessage(loadError, "Не удалось связаться с backend"));
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

  return { health, error };
};
