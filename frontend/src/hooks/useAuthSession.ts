import { useCallback, useEffect, useState } from "react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import type { AuthSession } from "@/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

export const useAuthSession = () => {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [session, setSession] = useState<AuthSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void apiClient.fetchSession(controller.signal)
      .then((payload) => {
        setSession(payload.authenticated ? payload : null);
        setStatus(payload.authenticated ? "authenticated" : "unauthenticated");
        setError(null);
      })
      .catch((loadError) => {
        if (controller.signal.aborted) {
          return;
        }
        setSession(null);
        setStatus("unauthenticated");
        setError(translateCommonApiError(loadError, "Не удалось проверить сессию"));
      });

    return () => controller.abort();
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const payload = await apiClient.login({ username, password });
      if (!payload.authenticated) {
        setSession(null);
        setStatus("unauthenticated");
        setError("Не удалось открыть сессию.");
        return;
      }
      setSession(payload);
      setStatus("authenticated");
    } catch (loginError) {
      setSession(null);
      setStatus("unauthenticated");
      setError(translateCommonApiError(loginError, "Не удалось войти"));
    } finally {
      setIsSubmitting(false);
    }
  }, []);

  const logout = useCallback(async () => {
    setIsLoggingOut(true);
    try {
      await apiClient.logout();
    } finally {
      setSession(null);
      setStatus("unauthenticated");
      setIsLoggingOut(false);
    }
  }, []);

  return {
    status,
    session,
    error,
    isSubmitting,
    isLoggingOut,
    login,
    logout,
  };
};
