import { BookText, Eye, Pencil, Trash2 } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { InstructionSummary } from "@/types";
import { formatDate } from "@/utils/format";
import {
  formatScopeTarget,
  instructionCategoryLabels,
} from "@/utils/workbenchPresentation";

type InstructionGroup = {
  id: string;
  label: string;
  description: string;
  items: InstructionSummary[];
};

type InstructionListProps = {
  instructionsCount: number;
  groupedInstructions: InstructionGroup[];
  isLoading: boolean;
  error: string | null;
  deletingInstructionId: string | null;
  onInspect: (instructionId: string) => void;
  onEdit: (instructionId: string) => void;
  onDelete: (instructionId: string) => void;
};

export function InstructionList({
  instructionsCount,
  groupedInstructions,
  isLoading,
  error,
  deletingInstructionId,
  onInspect,
  onEdit,
  onDelete,
}: InstructionListProps) {
  return (
    <Card className="order-2">
      <CardHeader>
        <SectionIntro
          badge={`${instructionsCount} saved`}
          badgeVariant="secondary"
          description="Проектные инструкции автоматически применяются только к активному RAG-проекту; сценарии остаются ручным выбором в Studio, а глобальные действуют шире."
          eyebrow="Instruction Stack"
          title="Инструкции RAG-проекта"
        />
      </CardHeader>
      <CardContent className="mt-0 space-y-5">
        {isLoading ? (
          <EmptyState description="Читаем инструкции из локального backend." title="Загружаем библиотеку" />
        ) : error ? (
          <EmptyState description={error} tone="danger" title="Не удалось загрузить инструкции" />
        ) : instructionsCount === 0 ? (
          <EmptyState
            description="Добавь первую инструкцию, чтобы система могла строить явный instruction stack."
            title="Пока пусто"
          />
        ) : (
          groupedInstructions.map(({ id, label, description, items }) => (
            <section className="space-y-3" key={id}>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base font-semibold text-foreground">{label}</h3>
                <Badge variant="secondary">{items.length}</Badge>
              </div>
              <p className="text-sm leading-6 text-muted-foreground">{description}</p>

              {items.length === 0 ? (
                <div className="rounded-[22px] border border-dashed border-border px-4 py-4 text-sm text-muted-foreground">
                  На этом уровне инструкций пока ничего не сохранено.
                </div>
              ) : (
                <div className="space-y-3">
                  {items.map((instruction) => (
                    <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={instruction.id}>
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div className="space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                              {instruction.title}
                            </h3>
                            <Badge variant="secondary">
                              {instructionCategoryLabels[instruction.category]}
                            </Badge>
                            <Badge variant={(instruction.active ?? true) ? "success" : "outline"}>
                              {(instruction.active ?? true) ? "active" : "inactive"}
                            </Badge>
                            <Badge variant="outline">rev {instruction.revision ?? 1}</Badge>
                          </div>
                          <p className="text-sm leading-6 text-muted-foreground">
                            target: {formatScopeTarget(instruction.scopeTargetId)}
                            {" · "}
                            обновлена {formatDate(instruction.updatedAt ?? instruction.createdAt)}
                          </p>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          <Button size="sm" type="button" variant="secondary" onClick={() => onInspect(instruction.id)}>
                            <Eye className="h-4 w-4" />
                            Открыть
                          </Button>
                          <Button size="sm" type="button" variant="outline" onClick={() => onEdit(instruction.id)}>
                            <Pencil className="h-4 w-4" />
                            Редактировать
                          </Button>
                          <Button
                            disabled={deletingInstructionId === instruction.id}
                            size="sm"
                            type="button"
                            variant="destructive"
                            onClick={() => onDelete(instruction.id)}
                          >
                            <Trash2 className="h-4 w-4" />
                            {deletingInstructionId === instruction.id ? "Удаляем..." : "Удалить"}
                          </Button>
                        </div>
                      </div>

                      <Separator />
                      <p className="text-sm leading-7 text-foreground">{instruction.preview}</p>
                    </article>
                  ))}
                </div>
              )}
            </section>
          ))
        )}
      </CardContent>
    </Card>
  );
}
