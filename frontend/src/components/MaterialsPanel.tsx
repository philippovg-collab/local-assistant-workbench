import { useState } from "react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { MaterialCatalogList } from "@/components/materials/MaterialCatalogList";
import { MaterialEditDialog } from "@/components/materials/MaterialEditDialog";
import { MaterialLineagePanel } from "@/components/materials/MaterialLineagePanel";
import { MaterialUploadForm } from "@/components/materials/MaterialUploadForm";
import { TextMaterialForm } from "@/components/materials/TextMaterialForm";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import type {
  MaterialLineageResponse,
  MaterialLineageOverrideInput,
  MaterialMetadataInput,
  MaterialSummary,
  MaterialUploadItemInput,
  MaterialUploadPolicy,
  MaterialVersionUploadInput,
  ReferenceProject,
  ReferenceWorkspace,
  UpdateMaterialInput,
} from "@/types";
import type { RagReadinessPresentation } from "@/utils/readiness";

type MaterialsPanelProps = {
  materials: MaterialSummary[];
  materialTotal: number;
  hasMoreMaterials: boolean;
  ragPresentation: RagReadinessPresentation;
  uploadPolicy: MaterialUploadPolicy | null;
  policyWarning: string | null;
  isLoading: boolean;
  isLoadingMore: boolean;
  error: string | null;
  message: string | null;
  actionError: string | null;
  deletingMaterialId: string | null;
  editingMaterialId: string | null;
  reindexingMaterialId: string | null;
  versionUploadingMaterialId: string | null;
  selectedLineage: MaterialLineageResponse | null;
  lineageError: string | null;
  loadingLineageMaterialId: string | null;
  metadataV1Enabled: boolean;
  referenceWorkspaces: ReferenceWorkspace[];
  referenceProjects: ReferenceProject[];
  isReferenceDataLoading: boolean;
  referenceDataError: string | null;
  activeWorkspaceKey?: string | null;
  activeWorkspaceName?: string | null;
  onCreateText: (input: {
    title: string;
    content: string;
    metadata?: MaterialMetadataInput;
    lineageOverride?: MaterialLineageOverrideInput;
  }) => Promise<unknown>;
  onUpload: (input: { items: MaterialUploadItemInput[] }) => Promise<unknown>;
  onUploadVersion: (materialId: string, input: MaterialVersionUploadInput) => Promise<unknown>;
  onEditMaterial: (materialId: string, input: UpdateMaterialInput) => Promise<unknown>;
  onDelete: (materialId: string) => Promise<unknown>;
  onReindex: (materialId: string) => Promise<unknown>;
  onLoadLineage: (materialId: string) => Promise<unknown>;
  onLoadMore: () => Promise<unknown>;
  onClearLineage: () => void;
};

const metadataDisabledReason =
  "Metadata rollout сейчас выключен: поля показаны для ориентира, но backend получит файл без metadata payload.";

export function MaterialsPanel({
  materials,
  materialTotal,
  hasMoreMaterials,
  ragPresentation,
  uploadPolicy,
  policyWarning,
  isLoading,
  isLoadingMore,
  error,
  message,
  actionError,
  deletingMaterialId,
  editingMaterialId,
  reindexingMaterialId,
  versionUploadingMaterialId,
  selectedLineage,
  lineageError,
  loadingLineageMaterialId,
  metadataV1Enabled,
  referenceWorkspaces,
  referenceProjects,
  isReferenceDataLoading,
  referenceDataError,
  activeWorkspaceKey,
  activeWorkspaceName,
  onCreateText,
  onUpload,
  onUploadVersion,
  onEditMaterial,
  onDelete,
  onReindex,
  onLoadLineage,
  onLoadMore,
  onClearLineage,
}: MaterialsPanelProps) {
  const [editingMaterial, setEditingMaterial] = useState<MaterialSummary | null>(null);
  const isLineageDialogOpen = Boolean(selectedLineage || lineageError);
  const normalizedActiveWorkspaceKey = activeWorkspaceKey?.trim() || "general";
  const normalizedActiveWorkspaceName = activeWorkspaceName?.trim() || normalizedActiveWorkspaceKey;

  return (
    <div className="space-y-6">
      <SectionIntro
        badge={`workspaceKey=${normalizedActiveWorkspaceKey}`}
        badgeVariant="default"
        description="Список, загрузка, редактирование и переиндексация ограничены активным RAG-проектом. Legacy business project остаётся только в metadata уже существующих материалов."
        eyebrow="Knowledge Base"
        title={`Материалы проекта: ${normalizedActiveWorkspaceName}`}
      />

      <div className="grid gap-6 xl:grid-cols-2">
        <TextMaterialForm
          isReferenceDataLoading={isReferenceDataLoading}
          metadataDisabledReason={metadataDisabledReason}
          metadataV1Enabled={metadataV1Enabled}
          activeWorkspaceKey={normalizedActiveWorkspaceKey}
          activeWorkspaceName={normalizedActiveWorkspaceName}
          referenceDataError={referenceDataError}
          referenceProjects={referenceProjects}
          referenceWorkspaces={referenceWorkspaces}
          onCreateText={onCreateText}
        />
        <MaterialUploadForm
          isReferenceDataLoading={isReferenceDataLoading}
          metadataDisabledReason={metadataDisabledReason}
          metadataV1Enabled={metadataV1Enabled}
          activeWorkspaceKey={normalizedActiveWorkspaceKey}
          activeWorkspaceName={normalizedActiveWorkspaceName}
          policyWarning={policyWarning}
          referenceDataError={referenceDataError}
          referenceProjects={referenceProjects}
          referenceWorkspaces={referenceWorkspaces}
          uploadPolicy={uploadPolicy}
          onUpload={onUpload}
        />
      </div>

      {message ? (
        <Alert variant="success">
          <AlertTitle>Изменения сохранены</AlertTitle>
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      ) : null}

      {actionError ? (
        <Alert variant="destructive">
          <AlertTitle>Операция не выполнена</AlertTitle>
          <AlertDescription>{actionError}</AlertDescription>
        </Alert>
      ) : null}

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить материалы</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      <MaterialCatalogList
        deletingMaterialId={deletingMaterialId}
        hasMoreMaterials={hasMoreMaterials}
        isLoading={isLoading}
        isLoadingMore={isLoadingMore}
        loadingLineageMaterialId={loadingLineageMaterialId}
        materialTotal={materialTotal}
        materials={materials}
        ragPresentation={ragPresentation}
        referenceProjects={referenceProjects}
        referenceWorkspaces={referenceWorkspaces}
        reindexingMaterialId={reindexingMaterialId}
        versionUploadingMaterialId={versionUploadingMaterialId}
        onDelete={onDelete}
        onEditMaterial={setEditingMaterial}
        onLoadLineage={onLoadLineage}
        onLoadMore={onLoadMore}
        onReindex={onReindex}
        onUploadVersion={onUploadVersion}
      />

      <Dialog
        open={isLineageDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            onClearLineage();
          }
        }}
      >
        <DialogContent className="max-h-[88vh] max-w-5xl overflow-y-auto">
          <DialogTitle className="sr-only">История версий материала</DialogTitle>
          <DialogDescription className="sr-only">
            Версии выбранного материала и причины перехода старых редакций в историю.
          </DialogDescription>
          <MaterialLineagePanel
            lineageError={lineageError}
            referenceProjects={referenceProjects}
            referenceWorkspaces={referenceWorkspaces}
            selectedLineage={selectedLineage}
          />
        </DialogContent>
      </Dialog>

      <MaterialEditDialog
        isReferenceDataLoading={isReferenceDataLoading}
        isSaving={editingMaterialId === editingMaterial?.id}
        material={editingMaterial}
        metadataDisabledReason={metadataDisabledReason}
        metadataV1Enabled={metadataV1Enabled}
        activeWorkspaceKey={normalizedActiveWorkspaceKey}
        activeWorkspaceName={normalizedActiveWorkspaceName}
        referenceDataError={referenceDataError}
        referenceProjects={referenceProjects}
        referenceWorkspaces={referenceWorkspaces}
        onOpenChange={(open) => {
          if (!open) {
            setEditingMaterial(null);
          }
        }}
        onUpdateMaterial={onEditMaterial}
      />
    </div>
  );
}
