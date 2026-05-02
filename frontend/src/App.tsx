import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Loader2, LockKeyhole } from "lucide-react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import { WorkbenchShell } from "@/components/workbench/WorkbenchShell";
import { kegocLogo } from "@/components/workbench/workbenchConfig";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AuthSession } from "@/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

function App() {
  const auth = useAuthSession();

  if (auth.status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center px-6">
        <div className="flex items-center gap-3 rounded-[24px] border border-border bg-surface-subtle px-5 py-4 text-sm font-semibold text-foreground shadow-soft">
          <Loader2 className="h-5 w-5 animate-spin text-primary" />
          Проверяем сессию
        </div>
      </div>
    );
  }

  if (auth.status !== "authenticated" || auth.session === null) {
    return (
      <LoginScreen
        error={auth.error}
        isSubmitting={auth.isSubmitting}
        onLogin={auth.login}
      />
    );
  }

  return (
    <WorkbenchShell
      isLoggingOut={auth.isLoggingOut}
      session={auth.session}
      onLogout={auth.logout}
    />
  );
}

function LoginScreen({
  error,
  isSubmitting,
  onLogin,
}: {
  error: string | null;
  isSubmitting: boolean;
  onLogin: (username: string, password: string) => Promise<void>;
}) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void onLogin(username, password);
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <main className="w-full max-w-[420px] rounded-[30px] border border-border bg-card p-6 shadow-panel">
        <div className="mb-7 flex flex-col items-center gap-3 text-center sm:flex-row sm:text-left">
          <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[22px] border border-sidebar-border bg-sidebar shadow-panel">
            <img alt="Логотип KEGOC" className="h-12 w-12 object-contain" src={kegocLogo} />
          </div>
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-primary">
              ПАНЕЛЬ УПРАВЛЕНИЯ
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-[-0.04em] text-foreground">KEGOC RAG</h1>
            <p className="mt-1 text-sm font-medium text-muted-foreground">Вход администратора</p>
          </div>
        </div>

        {error && (
          <Alert className="mb-5" variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="auth-username">Логин</Label>
            <Input
              autoComplete="username"
              id="auth-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="auth-password">Пароль</Label>
            <Input
              autoComplete="current-password"
              id="auth-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          <Button className="w-full" disabled={isSubmitting} type="submit">
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <LockKeyhole className="h-4 w-4" />}
            Войти
          </Button>
        </form>
      </main>
    </div>
  );
}

function useAuthSession() {
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
}

export default App;
