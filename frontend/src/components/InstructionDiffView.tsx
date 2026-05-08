import { Separator } from "@/components/ui/separator";
import type { InstructionRevisionDiff } from "@/types";
import { instructionDiffFieldLabels } from "@/components/instructionPresentation";

type InstructionDiffViewProps = {
  revisionDiff: InstructionRevisionDiff | null;
};

export function InstructionDiffView({ revisionDiff }: InstructionDiffViewProps) {
  if (!revisionDiff) {
    return null;
  }

  return (
    <div className="rounded-[22px] border border-field-border bg-field px-4 py-4">
      <p className="text-sm font-semibold text-foreground">
        Сравнение rev {revisionDiff.fromRevision} -&gt; rev {revisionDiff.toRevision}
      </p>
      <Separator className="my-3" />
      {revisionDiff.changes.length === 0 ? (
        <p className="text-sm leading-6 text-muted-foreground">Изменений между ревизиями нет.</p>
      ) : (
        <div className="space-y-3">
          {revisionDiff.changes.map((change) => (
            <div className="space-y-1 text-sm leading-6" key={`${change.field}-${change.fromValue}-${change.toValue}`}>
              <p className="font-medium text-foreground">
                {instructionDiffFieldLabels[change.field] ?? change.field}
              </p>
              <p className="text-muted-foreground">Было: {change.fromValue ?? "пусто"}</p>
              <p className="text-foreground">Стало: {change.toValue ?? "пусто"}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
