import { MaterialsPanel } from "@/components/MaterialsPanel";
import type { useMaterials } from "@/hooks/useMaterials";
import type { useReferenceData } from "@/hooks/useReferenceData";
import type { RagReadinessPresentation } from "@/utils/readiness";

type MaterialsTabProps = {
  activeWorkspaceKey: string;
  activeWorkspaceName: string;
  materials: ReturnType<typeof useMaterials>;
  metadataV1Enabled: boolean;
  ragPresentation: RagReadinessPresentation;
  referenceData: ReturnType<typeof useReferenceData>;
};

export function MaterialsTab({
  activeWorkspaceKey,
  activeWorkspaceName,
  materials,
  metadataV1Enabled,
  ragPresentation,
  referenceData,
}: MaterialsTabProps) {
  return (
    <MaterialsPanel
      actionError={materials.actionError}
      activeWorkspaceKey={activeWorkspaceKey}
      activeWorkspaceName={activeWorkspaceName}
      deletingMaterialId={materials.deletingMaterialId}
      editingMaterialId={materials.editingMaterialId}
      error={materials.error}
      hasMoreMaterials={materials.hasMoreMaterials}
      isLoading={materials.isLoading}
      isLoadingMore={materials.isLoadingMore}
      isReferenceDataLoading={referenceData.isLoading}
      lineageError={materials.lineageError}
      loadingLineageMaterialId={materials.loadingLineageMaterialId}
      materials={materials.materials}
      materialTotal={materials.materialTotal}
      metadataV1Enabled={metadataV1Enabled}
      message={materials.message}
      policyWarning={materials.policyWarning}
      ragPresentation={ragPresentation}
      referenceDataError={referenceData.error}
      referenceProjects={referenceData.projects}
      referenceWorkspaces={referenceData.workspaces}
      reindexingMaterialId={materials.reindexingMaterialId}
      selectedLineage={materials.selectedLineage}
      uploadPolicy={materials.uploadPolicy}
      versionUploadingMaterialId={materials.versionUploadingMaterialId}
      onClearLineage={materials.clearLineage}
      onCreateText={materials.createTextMaterial}
      onDelete={materials.deleteMaterial}
      onEditMaterial={materials.editMaterial}
      onLoadLineage={(materialId) => materials.loadLineage(materialId)}
      onLoadMore={materials.loadMoreMaterials}
      onReindex={materials.reindexMaterial}
      onUpload={materials.uploadMaterial}
      onUploadVersion={materials.uploadMaterialVersion}
    />
  );
}
