import { Info, ShieldCheck } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

type ChatFormActionsProps = {
  error: string | null;
  helperText: string;
  modelsError: string | null;
  isSubmitting: boolean;
  isCancelling: boolean;
  currentRunId?: string | null;
  isSubmitDisabled: boolean;
  submitIdleLabel: string;
  submitBusyLabel: string;
  cancelLabel: string;
  onCancelCurrentRun?: () => Promise<unknown>;
};

export function ChatFormActions({
  error,
  helperText,
  modelsError,
  isSubmitting,
  isCancelling,
  currentRunId,
  isSubmitDisabled,
  submitIdleLabel,
  submitBusyLabel,
  cancelLabel,
  onCancelCurrentRun,
}: ChatFormActionsProps) {
  return (
    <div className="surface-subtle flex flex-col gap-4 rounded-[24px] p-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-wrap gap-2">
          <Button disabled={isSubmitDisabled} type="submit">
            {isSubmitting ? submitBusyLabel : submitIdleLabel}
          </Button>
          {isSubmitting && currentRunId && onCancelCurrentRun ? (
            <Button
              disabled={isCancelling}
              type="button"
              variant="secondary"
              onClick={() => {
                void onCancelCurrentRun();
              }}
            >
              {isCancelling ? "Отменяем..." : cancelLabel}
            </Button>
          ) : null}
        </div>

        <div className="flex items-start gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-muted-foreground md:max-w-xl">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <p className="leading-6">
            {modelsError ? `Список моделей сейчас недоступен: ${modelsError}` : helperText}
          </p>
        </div>
      </div>

      <div className="flex items-start gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-muted-foreground">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p className="leading-6">
          Системная и workspace-инструкции подставляются автоматически. Здесь ты управляешь режимом ответа,
          временной инструкцией и сценарными инструкциями для текущего запроса.
        </p>
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось выполнить запрос</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}
    </div>
  );
}
