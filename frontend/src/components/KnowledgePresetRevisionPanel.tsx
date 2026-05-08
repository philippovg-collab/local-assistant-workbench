import { BookText, Database, History, RotateCcw } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type {
  KnowledgePresetDetail,
  KnowledgePresetRevisionDiff,
  KnowledgePresetRevisionDetail,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  presetDiffFieldLabels,
} from "@/components/knowledgePresetPresentation";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";

type KnowledgePresetRevisionPanelProps = {
  selectedPreset: KnowledgePresetDetail | null;
  revisions: KnowledgePresetRevisionDetail[];
  revisionDiff: KnowledgePresetRevisionDiff | null;
  selectedScopeSummary: string;
  isFacet: boolean;
  entityLabel: string;
  onLoadRevisionDiff: (
    presetId: string,
    fromRevision: number,
    toRevision: number,
  ) => Promise<KnowledgePresetRevisionDiff | null>;
  onRestoreRevision: (presetId: string, revision: number) => Promise<unknown>;
};

export function KnowledgePresetRevisionPanel({
  selectedPreset,
  revisions,
  revisionDiff,
  selectedScopeSummary,
  isFacet,
  entityLabel,
  onLoadRevisionDiff,
  onRestoreRevision,
}: KnowledgePresetRevisionPanelProps) {
  return (
    <Card className="order-3">
      <CardHeader>
        <SectionIntro
          badge={selectedPreset ? `rev ${selectedPreset.revision}` : "preview"}
          badgeVariant="secondary"
          eyebrow={isFacet ? "Facet Inspector" : "Preset Inspector"}
          title="Scope и история выбранного набора знаний"
        />
      </CardHeader>
      <CardContent className="mt-0 space-y-4">
        {selectedPreset ? (
          <>
            <div className="rounded-[24px] border border-field-border bg-field p-5">
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <Badge variant={selectedPreset.active ? "success" : "outline"}>
                  {selectedPreset.active ? "active" : "inactive"}
                </Badge>
                <Badge variant="outline">rev {selectedPreset.revision}</Badge>
              </div>
              <p className="text-sm leading-7 text-foreground">{selectedScopeSummary}</p>
            </div>

            <Separator />

            <div className="space-y-3">
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <History className="h-4 w-4 text-primary" />
                История ревизий
              </div>
              <PresetDiff revisionDiff={revisionDiff} />

              {revisions.length === 0 ? (
                <p className="text-sm leading-6 text-muted-foreground">История ревизий пока не загружена.</p>
              ) : (
                revisions.map((revision) => (
                  <article className="rounded-[22px] border border-field-border bg-field px-4 py-4" key={`${revision.presetId}-${revision.revision}`}>
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <strong className="text-sm font-semibold text-foreground">rev {revision.revision}</strong>
                          {revision.restoredFromRevision ? (
                            <Badge variant="warning">restore from rev {revision.restoredFromRevision}</Badge>
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
                        onClick={() => void onLoadRevisionDiff(revision.presetId, selectedPreset.revision, revision.revision)}
                      >
                        <BookText className="h-4 w-4" />
                        Сравнить
                      </Button>
                      <Button
                        size="sm"
                        type="button"
                        variant="outline"
                        onClick={() => void onRestoreRevision(revision.presetId, revision.revision)}
                      >
                        <RotateCcw className="h-4 w-4" />
                        Восстановить
                      </Button>
                    </div>
                    <Separator className="my-3" />
                    <p className="text-sm leading-6 text-foreground">{formatRevisionScope(revision)}</p>
                  </article>
                ))
              )}
            </div>
          </>
        ) : (
          <EmptyState
            description={`Открой любой ${entityLabel} слева, чтобы увидеть его scope и историю ревизий.`}
            icon={Database}
            title={isFacet ? "Фасет не выбран" : "Preset не выбран"}
          />
        )}
      </CardContent>
    </Card>
  );
}

function PresetDiff({ revisionDiff }: { revisionDiff: KnowledgePresetRevisionDiff | null }) {
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
              <p className="font-medium text-foreground">{presetDiffFieldLabels[change.field] ?? change.field}</p>
              <p className="text-muted-foreground">Было: {change.fromValue ?? "пусто"}</p>
              <p className="text-foreground">Стало: {change.toValue ?? "пусто"}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

const formatRevisionScope = (revision: KnowledgePresetRevisionDetail) => {
  const documentTypeText = (revision.scope.documentTypes ?? []).length > 0
    ? (revision.scope.documentTypes ?? []).map((item) => documentTypeLabels[item]).join(", ")
    : "Без ограничения по типам";
  return [
    documentTypeText,
    (revision.scope.documentStatuses ?? []).length > 0
      ? `statuses: ${(revision.scope.documentStatuses ?? []).map((item) => documentStatusLabels[item]).join(", ")}`
      : "",
    (revision.scope.projectKeys ?? []).length > 0 ? `projects: ${(revision.scope.projectKeys ?? []).join(", ")}` : "",
    (revision.scope.languageCodes ?? []).length > 0
      ? `languages: ${(revision.scope.languageCodes ?? []).map((item) => materialLanguageCodeLabels[item]).join(", ")}`
      : "",
    revision.scope.tags.length > 0 ? `tags: ${revision.scope.tags.join(", ")}` : "",
    revision.scope.workspaceKey ? `workspace: ${revision.scope.workspaceKey}` : "",
    revision.scope.uploadedTodayOnly ? "today uploads only" : "",
  ].filter(Boolean).join(" · ");
};
