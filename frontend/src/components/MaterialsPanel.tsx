import { SectionIntro } from "@/components/app/SectionIntro";
import { MaterialCatalogList } from "@/components/materials/MaterialCatalogList";
import { MaterialLineagePanel } from "@/components/materials/MaterialLineagePanel";
import { MaterialUploadForm } from "@/components/materials/MaterialUploadForm";
import { TextMaterialForm } from "@/components/materials/TextMaterialForm";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import type {
  MaterialLineageResponse,
  MaterialMetadataInput,
  MaterialSummary,
  MaterialUploadItemInput,
  MaterialUploadPolicy,
  MaterialVersionUploadInput,
  ReferenceProject,
  ReferenceWorkspace,
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
  onCreateText: (input: { title: string; content: string; metadata?: MaterialMetadataInput }) => Promise<unknown>;
  onUpload: (input: { items: MaterialUploadItemInput[] }) => Promise<unknown>;
  onUploadVersion: (materialId: string, input: MaterialVersionUploadInput) => Promise<unknown>;
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
  onCreateText,
  onUpload,
  onUploadVersion,
  onDelete,
  onReindex,
  onLoadLineage,
  onLoadMore,
  onClearLineage,
}: MaterialsPanelProps) {
  return (
    <div className="space-y-6">
      <SectionIntro
        badge="GET /api/materials"
        badgeVariant="default"
        description="Материалы сначала принимаются backend и попадают в каталог, а embeddings/index строятся отдельным lifecycle со статусами `PENDING`, `IN_PROGRESS`, `READY`, `PARTIAL_READY` и `FAILED`."
        eyebrow="Knowledge Base"
        title="Материалы для RAG"
      />

      <div className="grid gap-6 xl:grid-cols-2">
        <TextMaterialForm
          isReferenceDataLoading={isReferenceDataLoading}
          metadataDisabledReason={metadataDisabledReason}
          metadataV1Enabled={metadataV1Enabled}
          referenceDataError={referenceDataError}
          referenceProjects={referenceProjects}
          referenceWorkspaces={referenceWorkspaces}
          onCreateText={onCreateText}
        />
        <MaterialUploadForm
          isReferenceDataLoading={isReferenceDataLoading}
          metadataDisabledReason={metadataDisabledReason}
          metadataV1Enabled={metadataV1Enabled}
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
        onLoadLineage={onLoadLineage}
        onLoadMore={onLoadMore}
        onReindex={onReindex}
        onUploadVersion={onUploadVersion}
      />

      <MaterialLineagePanel
        lineageError={lineageError}
        referenceProjects={referenceProjects}
        referenceWorkspaces={referenceWorkspaces}
        selectedLineage={selectedLineage}
        onClearLineage={onClearLineage}
      />
    </div>
  );
}
