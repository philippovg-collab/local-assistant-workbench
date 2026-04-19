import { ListOrdered, Sparkles } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";
import type { InstructionSummary } from "@/types";
import {
  instructionCategoryLabels,
  instructionScopeLabels,
} from "@/utils/workbenchPresentation";

type InstructionSelectorProps = {
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  label: string;
  emptyStateMessage: string;
};

export function InstructionSelector({
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  label,
  emptyStateMessage,
}: InstructionSelectorProps) {
  const selectedById = new Map(
    selectedInstructionIds.map((instructionId, index) => [instructionId, index + 1]),
  );
  const orderedSelectedInstructions = selectedInstructionIds
    .map((instructionId) => instructions.find((instruction) => instruction.id === instructionId))
    .filter((instruction): instruction is InstructionSummary => instruction !== undefined);

  return (
    <fieldset className="space-y-4">
      <legend className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Sparkles className="h-4 w-4 text-primary" />
        {label}
      </legend>

      {instructions.length === 0 ? (
        <EmptyState
          description={emptyStateMessage}
          title="Инструкции пока недоступны"
        />
      ) : (
        <>
          <div className="grid gap-3 lg:grid-cols-2">
            {instructions.map((instruction) => {
              const selectedOrder = selectedById.get(instruction.id);
              const scopeLevel = instruction.scopeLevel ?? "chat_scenario";
              const revision = instruction.revision ?? 1;

              return (
                <div
                  className={cn(
                    "group flex cursor-pointer flex-col gap-3 rounded-[24px] border px-4 py-4 transition",
                    selectedOrder
                      ? "border-primary/30 bg-primary/8 shadow-soft"
                      : "surface-subtle hover:border-primary/20 hover:bg-surface-subtle-strong",
                  )}
                  key={instruction.id}
                >
                  <div className="flex items-start gap-3">
                    <Checkbox
                      aria-label={`Выбрать инструкцию ${instruction.title}`}
                      checked={selectedOrder !== undefined}
                      onCheckedChange={() => onToggleInstruction(instruction.id)}
                    />

                    <div className="min-w-0 space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <strong className="text-sm font-semibold text-foreground">
                          {instruction.title}
                        </strong>
                        <Badge variant={selectedOrder ? "default" : "secondary"}>
                          {selectedOrder ? `#${selectedOrder}` : instructionCategoryLabels[instruction.category]}
                        </Badge>
                        <Badge variant="outline">{instructionScopeLabels[scopeLevel]}</Badge>
                        <Badge variant="outline">rev {revision}</Badge>
                      </div>
                      <p className="text-sm leading-6 text-muted-foreground">{instruction.preview}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="surface-subtle rounded-[24px] p-4">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
              <ListOrdered className="h-4 w-4 text-primary" />
              Порядок применения
            </div>

            {orderedSelectedInstructions.length === 0 ? (
              <p className="text-sm leading-6 text-muted-foreground">
                Если ничего не выбрать, backend выполнит запрос только с базовым system prompt.
              </p>
            ) : (
              <ol className="space-y-2">
                {orderedSelectedInstructions.map((instruction, index) => (
                  <li
                    className="flex items-center justify-between gap-3 rounded-2xl border border-field-border bg-field px-4 py-3"
                    key={instruction.id}
                  >
                    <div className="flex items-center gap-3">
                      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                        {index + 1}
                      </span>
                      <div className="space-y-1">
                        <strong className="block text-sm font-medium text-foreground">{instruction.title}</strong>
                        <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">
                          {instructionScopeLabels[instruction.scopeLevel ?? "chat_scenario"]}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary">{instructionCategoryLabels[instruction.category]}</Badge>
                      <Badge variant="outline">rev {instruction.revision ?? 1}</Badge>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </>
      )}
    </fieldset>
  );
}
