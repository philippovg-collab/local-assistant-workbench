import { useState } from "react";
import { FileClock, FileText, Filter, History, RefreshCcw, Trash2, Upload } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { MaterialMetadataDisplay } from "@/components/MaterialMetadataDisplay";
import { MaterialVersionUploadForm } from "@/components/materials/MaterialVersionUploadForm";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type {
  MaterialSummary,
  MaterialVersionUploadInput,
  ReferenceProject,
  ReferenceWorkspace,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  filterLabels,
  formatRetryMeta,
  isReindexAllowed,
  materialStatusLabel,
  materialStatusVariant,
  visibleMaterialsForFilter,
  versionStateLabel,
  type MaterialFilterMode,
} from "@/utils/materialPresentation";
import { isActiveMaterialVersion, type RagReadinessPresentation } from "@/utils/readiness";

type MaterialCatalogListProps = {
  materials: MaterialSummary[];
  materialTotal: number;
  hasMoreMaterials: boolean;
  ragPresentation: RagReadinessPresentation;
  isLoading: boolean;
  isLoadingMore: boolean;
  deletingMaterialId: string | null;
  reindexingMaterialId: string | null;
  versionUploadingMaterialId: string | null;
  loadingLineageMaterialId: string | null;
  referenceProjects?: ReferenceProject[];
  referenceWorkspaces?: ReferenceWorkspace[];
  onDelete: (materialId: string) => Promise<unknown>;
  onReindex: (materialId: string) => Promise<unknown>;
  onUploadVersion: (materialId: string, input: MaterialVersionUploadInput) => Promise<unknown>;
  onLoadLineage: (materialId: string) => Promise<unknown>;
  onLoadMore: () => Promise<unknown>;
};

export function MaterialCatalogList({
  materials,
  materialTotal,
  hasMoreMaterials,
  ragPresentation,
  isLoading,
  isLoadingMore,
  deletingMaterialId,
  reindexingMaterialId,
  versionUploadingMaterialId,
  loadingLineageMaterialId,
  referenceProjects = [],
  referenceWorkspaces = [],
  onDelete,
  onReindex,
  onUploadVersion,
  onLoadLineage,
  onLoadMore,
}: MaterialCatalogListProps) {
  const [filterMode, setFilterMode] = useState<MaterialFilterMode>("active");
  const [versionUploadMaterialId, setVersionUploadMaterialId] = useState<string | null>(null);
  const visibleMaterials = visibleMaterialsForFilter(materials, filterMode);
  const effectiveMaterialTotal = Math.max(materialTotal, materials.length);

  const handleDelete = async (materialId: string) => {
    const material = materials.find((item) => item.id === materialId);
    const confirmed = window.confirm(
      `Удалить материал "${material?.title ?? materialId}" из локального хранилища?`,
    );

    if (!confirmed) {
      return;
    }

    await onDelete(materialId);
  };

  return (
    <Card>
      <CardHeader className="gap-5">
        <SectionIntro
          badge={ragPresentation.badgeLabel}
          badgeVariant="secondary"
          description="Список версий, фильтры проблемных материалов и операции reindex/delete собраны в одном месте."
          eyebrow="Catalog"
          title="Локальное хранилище"
        />

        <p className="text-sm text-muted-foreground">
          Показано {materials.length} из {effectiveMaterialTotal}
        </p>

        {ragPresentation.materialsMessage ? (
          <Alert
            variant={
              ragPresentation.materialsMessageClassName === "warning-state"
                ? "warning"
                : "default"
            }
          >
            <AlertTitle>Readiness context</AlertTitle>
            <AlertDescription>{ragPresentation.materialsMessage}</AlertDescription>
          </Alert>
        ) : null}

        <div className="flex flex-wrap items-center gap-3">
          <span className="inline-flex items-center gap-2 text-sm font-medium text-foreground">
            <Filter className="h-4 w-4 text-primary" />
            Фильтр материалов
          </span>
          {(["active", "all", "problematic"] as MaterialFilterMode[]).map((mode) => (
            <Button
              key={mode}
              size="sm"
              type="button"
              variant={filterMode === mode ? "default" : "secondary"}
              onClick={() => setFilterMode(mode)}
            >
              {filterLabels[mode]}
            </Button>
          ))}
        </div>
      </CardHeader>
      <CardContent className="mt-0 space-y-4">
        {isLoading ? (
          <EmptyState
            description="Читаем локальное хранилище backend."
            icon={FileClock}
            title="Загружаем материалы"
          />
        ) : materials.length === 0 ? (
          <EmptyState
            description="Добавь хотя бы один материал, чтобы RAG-режим мог построить ответ по контексту."
            icon={FileText}
            title="Пока пусто"
          />
        ) : visibleMaterials.length === 0 ? (
          <EmptyState
            description={
              filterMode === "problematic"
                ? "FAILED и PARTIAL_READY материалы сейчас не найдены."
                : "Переключи фильтр, чтобы посмотреть другие версии материалов."
            }
            icon={Filter}
            title="По этому фильтру пока пусто"
          />
        ) : (
          <div className="space-y-4">
            <div className="space-y-3">
              {visibleMaterials.map((material) => (
                <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={material.id}>
                  <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                          {material.title}
                        </h3>
                        <Badge variant={materialStatusVariant[material.status]}>
                          {materialStatusLabel[material.status]}
                        </Badge>
                        <Badge variant={isActiveMaterialVersion(material) ? "default" : "secondary"}>
                          {versionStateLabel(material)}
                        </Badge>
                      </div>
                      <p className="text-sm leading-6 text-muted-foreground">
                        {material.originalFileName ?? material.sourceType} · {material.contentLength} символов · создан{" "}
                        {formatDate(material.createdAt)}
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        disabled={loadingLineageMaterialId === material.id}
                        size="sm"
                        type="button"
                        variant="secondary"
                        onClick={() => void onLoadLineage(material.id)}
                      >
                        <History className="h-4 w-4" />
                        {loadingLineageMaterialId === material.id ? "Загружаем историю..." : "История"}
                      </Button>
                      {isReindexAllowed(material) ? (
                        <Button
                          disabled={reindexingMaterialId === material.id}
                          size="sm"
                          type="button"
                          variant="outline"
                          onClick={() => void onReindex(material.id)}
                        >
                          <RefreshCcw className="h-4 w-4" />
                          {reindexingMaterialId === material.id ? "Повторяем..." : "Повторить индекс"}
                        </Button>
                      ) : null}
                      {isActiveMaterialVersion(material) ? (
                        <Button
                          disabled={versionUploadingMaterialId === material.id}
                          size="sm"
                          type="button"
                          variant="outline"
                          onClick={() => setVersionUploadMaterialId((current) => (
                            current === material.id ? null : material.id
                          ))}
                        >
                          <Upload className="h-4 w-4" />
                          {versionUploadingMaterialId === material.id
                            ? "Загружаем..."
                            : "Загрузить новую версию"}
                        </Button>
                      ) : null}
                      <Button
                        disabled={deletingMaterialId === material.id}
                        size="sm"
                        type="button"
                        variant="destructive"
                        onClick={() => void handleDelete(material.id)}
                      >
                        <Trash2 className="h-4 w-4" />
                        {deletingMaterialId === material.id ? "Удаляем..." : "Удалить"}
                      </Button>
                    </div>
                  </div>

                  <Separator />

                  <p className="text-sm leading-7 text-foreground">{material.preview}</p>

                  <MaterialMetadataDisplay
                    metadata={material.metadata}
                    references={{ projects: referenceProjects, workspaces: referenceWorkspaces }}
                  />

                  {versionUploadMaterialId === material.id ? (
                    <MaterialVersionUploadForm
                      isUploading={versionUploadingMaterialId === material.id}
                      material={material}
                      onCancel={() => setVersionUploadMaterialId(null)}
                      onUploadVersion={onUploadVersion}
                    />
                  ) : null}

                  <div className="flex flex-wrap gap-3 text-sm leading-6 text-muted-foreground">
                    <span>Обновлён {formatDate(material.updatedAt ?? material.createdAt)}</span>
                    {(material.indexingAttempts ?? 0) > 0 ? (
                      <span>Попыток индексации: {material.indexingAttempts}</span>
                    ) : null}
                    {formatRetryMeta(material) ? <span>{formatRetryMeta(material)}</span> : null}
                  </div>

                  {!isActiveMaterialVersion(material) ? (
                    <Alert variant="warning">
                      <AlertTitle>Историческая версия</AlertTitle>
                      <AlertDescription>
                        Эта версия сохранена для аудита, но исключена из retrieval и ready-count.
                      </AlertDescription>
                    </Alert>
                  ) : null}

                  {material.statusReasonMessage ? (
                    <Alert variant={material.status === "FAILED" ? "destructive" : "default"}>
                      <AlertTitle>Причина статуса</AlertTitle>
                      <AlertDescription>{material.statusReasonMessage}</AlertDescription>
                    </Alert>
                  ) : null}
                </article>
              ))}
            </div>
            {hasMoreMaterials ? (
              <Button
                className="w-full sm:w-auto"
                disabled={isLoadingMore}
                type="button"
                variant="secondary"
                onClick={() => void onLoadMore()}
              >
                <RefreshCcw className="h-4 w-4" />
                {isLoadingMore ? "Загружаем..." : "Показать ещё"}
              </Button>
            ) : null}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
