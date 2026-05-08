import { BookText, History, RotateCcw } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { InstructionDiffView } from "@/components/InstructionDiffView";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type {
  InstructionDetail,
  InstructionRevisionDiff,
  InstructionRevisionDetail,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  formatScopeTarget,
  instructionScopeLabels,
} from "@/utils/workbenchPresentation";

type InstructionRevisionPanelProps = {
  selectedInstruction: InstructionDetail | null;
  revisions: InstructionRevisionDetail[];
  revisionDiff: InstructionRevisionDiff | null;
  detailError: string | null;
  isLoadingDetail: boolean;
  onLoadInstructionDiff: (
    instructionId: string,
    fromRevision: number,
    toRevision: number,
  ) => Promise<InstructionRevisionDiff | null>;
  onRestoreInstructionRevision: (instructionId: string, revision: number) => Promise<unknown>;
};

export function InstructionRevisionPanel({
  selectedInstruction,
  revisions,
  revisionDiff,
  detailError,
  isLoadingDetail,
  onLoadInstructionDiff,
  onRestoreInstructionRevision,
}: InstructionRevisionPanelProps) {
  return (
    <Card className="order-3">
      <CardHeader>
        <SectionIntro
          badge={selectedInstruction ? `rev ${selectedInstruction.revision ?? 1}` : "preview"}
          badgeVariant="secondary"
          eyebrow="Inspector"
          title="Текст и история выбранной инструкции"
        />
      </CardHeader>
      <CardContent className="mt-0 space-y-4">
        {isLoadingDetail ? (
          <EmptyState description="Подтягиваем текст и историю ревизий." title="Загружаем инструкцию" />
        ) : detailError ? (
          <EmptyState description={detailError} tone="danger" title="Не удалось загрузить инструкцию" />
        ) : selectedInstruction ? (
          <>
            <div className="space-y-3 rounded-[24px] border border-field-border bg-field p-5">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary">
                  {instructionScopeLabels[selectedInstruction.scopeLevel ?? "chat_scenario"]}
                </Badge>
                <Badge variant="outline">
                  target: {formatScopeTarget(selectedInstruction.scopeTargetId)}
                </Badge>
                <Badge variant={(selectedInstruction.active ?? true) ? "success" : "outline"}>
                  {(selectedInstruction.active ?? true) ? "active" : "inactive"}
                </Badge>
              </div>
              <p className="text-sm leading-7 text-foreground">{selectedInstruction.content}</p>
            </div>

            <Separator />

            <div className="space-y-3">
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <History className="h-4 w-4 text-primary" />
                История ревизий
              </div>

              <InstructionDiffView revisionDiff={revisionDiff} />

              {revisions.length === 0 ? (
                <p className="text-sm leading-6 text-muted-foreground">
                  История ревизий пока не загружена или ещё не содержит прошлых версий.
                </p>
              ) : (
                <div className="space-y-3">
                  {revisions.map((revision) => (
                    <article
                      className="rounded-[22px] border border-field-border bg-field px-4 py-4"
                      key={`${revision.instructionId}-${revision.revision}`}
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <strong className="text-sm font-semibold text-foreground">
                              rev {revision.revision}
                            </strong>
                            {revision.restoredFromRevision ? (
                              <Badge variant="warning">
                                restore from rev {revision.restoredFromRevision}
                              </Badge>
                            ) : null}
                          </div>
                          <p className="text-sm leading-6 text-muted-foreground">
                            {formatDate(revision.updatedAt ?? revision.createdAt)}
                          </p>
                        </div>

                        <Button
                          size="sm"
                          type="button"
                          variant="outline"
                          onClick={() =>
                            void onLoadInstructionDiff(
                              revision.instructionId,
                              selectedInstruction.revision ?? revision.revision,
                              revision.revision,
                            )}
                        >
                          <BookText className="h-4 w-4" />
                          Сравнить
                        </Button>
                        <Button
                          size="sm"
                          type="button"
                          variant="outline"
                          onClick={() => void onRestoreInstructionRevision(revision.instructionId, revision.revision)}
                        >
                          <RotateCcw className="h-4 w-4" />
                          Восстановить
                        </Button>
                      </div>
                      <Separator className="my-3" />
                      <p className="text-sm leading-6 text-foreground">{revision.content}</p>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </>
        ) : (
          <EmptyState
            description="Открой любую инструкцию слева, чтобы увидеть полный текст и историю её ревизий."
            icon={BookText}
            title="Инструкция не выбрана"
          />
        )}
      </CardContent>
    </Card>
  );
}
