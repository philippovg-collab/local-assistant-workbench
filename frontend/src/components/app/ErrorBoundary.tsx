import React from "react";
import { currentClientPath, logClientEvent, payloadFromUnknown } from "@/utils/clientEvents";

type ErrorBoundaryProps = {
  children: React.ReactNode;
};

type ErrorBoundaryState = {
  failed: boolean;
};

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    failed: false,
  };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return {
      failed: true,
    };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    const payload = payloadFromUnknown(error);
    logClientEvent({
      severity: "fatal",
      type: "react.error_boundary",
      message: payload.message,
      stack: payload.stack,
      componentStack: info.componentStack ?? undefined,
      path: currentClientPath(),
      requestId: payload.requestId,
      metadata: payload.metadata,
    });
  }

  render() {
    if (this.state.failed) {
      return (
        <main className="flex min-h-screen items-center justify-center bg-background px-6 py-10 text-foreground">
          <section className="max-w-md space-y-3 text-center">
            <h1 className="text-2xl font-semibold">Не удалось отобразить рабочую область</h1>
            <p className="text-sm text-muted-foreground">Обновите страницу и продолжите работу.</p>
          </section>
        </main>
      );
    }

    return this.props.children;
  }
}
