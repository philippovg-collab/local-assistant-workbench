import { GitMerge, WandSparkles } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import type { AppliedInstruction, InstructionTraceEntry } from "@/types";
import {
  instructionCategoryLabels,
  instructionScopeLabels,
} from "@/utils/workbenchPresentation";

type AppliedInstructionListProps = {
  appliedInstructions: AppliedInstruction[];
  instructionTrace?: InstructionTraceEntry[];
};

export function AppliedInstructionList({
  appliedInstructions,
  instructionTrace = [],
}: AppliedInstructionListProps) {
  const traceToRender = instructionTrace.length > 0
      ? instructionTrace
      : appliedInstructions.map((instruction) => ({
        instructionId: instruction.id,
        title: instruction.title,
        category: instruction.category,
        scopeLevel: instruction.scopeLevel ?? "chat_scenario",
        scopeTargetId: instruction.scopeTargetId,
        revision: instruction.revision ?? 1,
        active: true,
        temporary: (instruction.scopeLevel ?? "chat_scenario") === "request_temporary",
        contentPreview: "",
      }));

  return (
    <article className="surface-subtle space-y-4 rounded-[24px] p-5">
      <SectionIntro
        badge={`${traceToRender.length} layer(s)`}
        badgeVariant="secondary"
        eyebrow="Prompt Stack"
        title="Применённые инструкции"
      />

      {traceToRender.length === 0 ? (
        <EmptyState
          description="Запрос был выполнен только с базовой prompt policy для выбранного режима."
          title="Дополнительные инструкции не были применены"
        />
      ) : (
        <ol className="space-y-3">
          {traceToRender.map((instruction, index) => (
            <li
              className="rounded-[22px] border border-field-border bg-field px-4 py-4"
              key={`${instruction.instructionId ?? instruction.title}-${instruction.scopeLevel}-${instruction.revision}-${index}`}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                    <WandSparkles className="h-4 w-4" />
                  </span>
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <strong className="block text-sm font-semibold text-foreground">
                        {index + 1}. {instruction.title}
                      </strong>
                      <Badge variant={instruction.temporary ? "warning" : "secondary"}>
                        {instructionScopeLabels[instruction.scopeLevel]}
                      </Badge>
                      <Badge variant="outline">rev {instruction.revision}</Badge>
                    </div>
                    <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                      {instructionCategoryLabels[instruction.category]}
                      {instruction.scopeTargetId ? ` · ${instruction.scopeTargetId}` : ""}
                    </p>
                  </div>
                </div>

                {instruction.temporary ? <Badge variant="warning">temporary</Badge> : null}
              </div>

              {instruction.contentPreview ? (
                <>
                  <Separator className="my-3" />
                  <div className="flex items-start gap-3">
                    <GitMerge className="mt-1 h-4 w-4 shrink-0 text-primary" />
                    <p className="text-sm leading-6 text-foreground">{instruction.contentPreview}</p>
                  </div>
                </>
              ) : null}
            </li>
          ))}
        </ol>
      )}
    </article>
  );
}
