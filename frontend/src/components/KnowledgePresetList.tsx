import { Eye, Pencil, Trash2 } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import type { KnowledgePresetSummary } from "@/types";

type KnowledgePresetListProps = {
  visiblePresets: KnowledgePresetSummary[];
  isFacet: boolean;
  entityTitle: string;
  activeProjectLabel: string;
  isLoading: boolean;
  error: string | null;
  onLoadInspector: (presetId: string) => void;
  onEdit: (presetId: string) => void;
  onDelete: (presetId: string) => void;
};

export function KnowledgePresetList({
  visiblePresets,
  isFacet,
  entityTitle,
  activeProjectLabel,
  isLoading,
  error,
  onLoadInspector,
  onEdit,
  onDelete,
}: KnowledgePresetListProps) {
  return (
    <Card className="order-2">
      <CardHeader>
        <SectionIntro
          badge={`${visiblePresets.length} ${isFacet ? "facet" : "preset"}(s)`}
          badgeVariant="secondary"
          description={isFacet
            ? "Фасеты проекта задают сохранённые фильтры поиска по тем же полям, которые используются при загрузке материалов."
            : "Пресеты проекта ограничивают retrieval внутри активного RAG-проекта. Shared preset без workspace тоже можно видеть, но итоговый запрос всё равно остаётся в активном проекте."}
          eyebrow="Knowledge Scope"
          title={`${entityTitle}: ${activeProjectLabel}`}
        />
      </CardHeader>
      <CardContent className="mt-0 space-y-4">
        {isLoading ? (
          <EmptyState description={isFacet ? "Читаем фасеты проекта." : "Читаем presets корпуса."} title={isFacet ? "Загружаем фасеты" : "Загружаем knowledge presets"} />
        ) : error ? (
          <EmptyState description={error} tone="danger" title={isFacet ? "Не удалось загрузить фасеты" : "Не удалось загрузить presets"} />
        ) : visiblePresets.length === 0 ? (
          <EmptyState
            description={isFacet
              ? "Сохрани первый фасет, чтобы быстро включать нужные фильтры поиска внутри проекта."
              : "Сохрани первый preset, чтобы retrieval можно было ограничивать релевантными фасетами внутри проекта."}
            title={isFacet ? "Фасеты проекта пока пусты" : "Пресеты проекта пока пусты"}
          />
        ) : (
          <div className="space-y-3">
            {visiblePresets.map((preset) => (
              <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={preset.id}>
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">{preset.name}</h3>
                      <Badge variant={preset.active ? "success" : "outline"}>
                        {preset.active ? "active" : "inactive"}
                      </Badge>
                      <Badge variant="outline">rev {preset.revision}</Badge>
                      {!preset.workspaceKey ? <Badge variant="secondary">shared</Badge> : null}
                    </div>
                    <p className="text-sm leading-6 text-muted-foreground">
                      {preset.description?.trim() || "Описание не задано."}
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <Button size="sm" type="button" variant="secondary" onClick={() => onLoadInspector(preset.id)}>
                      <Eye className="h-4 w-4" />
                      Открыть
                    </Button>
                    <Button size="sm" type="button" variant="outline" onClick={() => onEdit(preset.id)}>
                      <Pencil className="h-4 w-4" />
                      Редактировать
                    </Button>
                    <Button size="sm" type="button" variant="destructive" onClick={() => onDelete(preset.id)}>
                      <Trash2 className="h-4 w-4" />
                      Удалить
                    </Button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
