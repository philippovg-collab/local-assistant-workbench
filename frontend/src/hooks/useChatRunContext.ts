import { useEffect, useState } from "react";
import { apiClient, isApiClientError } from "@/api/client";
import type { ChatRunContextDetail } from "@/types";

type UseChatRunContextOptions = {
  enabled?: boolean;
};

export const useChatRunContext = (
  runId: string | null,
  { enabled = true }: UseChatRunContextOptions = {},
) => {
  const [snapshot, setSnapshot] = useState<ChatRunContextDetail | null>(null);
  const [status, setStatus] = useState<"idle" | "loading" | "missing" | "error">("idle");

  useEffect(() => {
    if (!enabled || !runId) {
      setSnapshot(null);
      setStatus("idle");
      return;
    }

    const controller = new AbortController();
    setStatus("loading");
    apiClient.fetchChatRunContext(runId, controller.signal)
      .then((payload) => {
        setSnapshot(payload);
        setStatus("idle");
      })
      .catch((error) => {
        if (controller.signal.aborted) {
          return;
        }
        setSnapshot(null);
        setStatus(isApiClientError(error) && error.status === 404 ? "missing" : "error");
      });
    return () => controller.abort();
  }, [enabled, runId]);

  return {
    snapshot,
    status,
    isLoading: status === "loading",
  };
};
