import { useMaterialCatalog } from "@/hooks/useMaterialCatalog";
import { useMaterialLineage } from "@/hooks/useMaterialLineage";
import { useMaterialMutations } from "@/hooks/useMaterialMutations";
import { useMaterialUploadPolicy } from "@/hooks/useMaterialUploadPolicy";

export { useMaterialCatalog } from "@/hooks/useMaterialCatalog";
export { useMaterialLineage } from "@/hooks/useMaterialLineage";
export { useMaterialMutations } from "@/hooks/useMaterialMutations";
export { useMaterialUploadPolicy } from "@/hooks/useMaterialUploadPolicy";

export const useMaterials = (options: { workspaceKey?: string | null } = {}) => {
  const { workspaceKey = null } = options;
  const uploadPolicyState = useMaterialUploadPolicy();
  const catalog = useMaterialCatalog(uploadPolicyState.uploadPolicy, workspaceKey);
  const lineage = useMaterialLineage(uploadPolicyState.uploadPolicy, workspaceKey);
  const mutations = useMaterialMutations({
    workspaceKey,
    uploadPolicy: uploadPolicyState.uploadPolicy,
    ensureUploadPolicy: uploadPolicyState.ensureUploadPolicy,
    loadMaterials: () => catalog.loadMaterials(),
    clearLineageIfContains: lineage.clearLineageIfContains,
    refreshLineageIfContains: lineage.refreshLineageIfContains,
  });

  return {
    materials: catalog.materials,
    materialTotal: catalog.materialTotal,
    materialPageLimit: catalog.materialPageLimit,
    hasMoreMaterials: catalog.hasMoreMaterials,
    uploadPolicy: uploadPolicyState.uploadPolicy,
    policyWarning: uploadPolicyState.policyWarning,
    error: catalog.error,
    isLoading: catalog.isLoading,
    isLoadingMore: catalog.isLoadingMore,
    message: mutations.message,
    actionError: mutations.actionError,
    deletingMaterialId: mutations.deletingMaterialId,
    editingMaterialId: mutations.editingMaterialId,
    reindexingMaterialId: mutations.reindexingMaterialId,
    versionUploadingMaterialId: mutations.versionUploadingMaterialId,
    selectedLineage: lineage.selectedLineage,
    lineageError: lineage.lineageError,
    loadingLineageMaterialId: lineage.loadingLineageMaterialId,
    createTextMaterial: mutations.createTextMaterial,
    uploadMaterial: mutations.uploadMaterial,
    uploadMaterialVersion: mutations.uploadMaterialVersion,
    editMaterial: mutations.editMaterial,
    deleteMaterial: mutations.deleteMaterial,
    reindexMaterial: mutations.reindexMaterial,
    loadLineage: lineage.loadLineage,
    loadMoreMaterials: catalog.loadMoreMaterials,
    clearLineage: lineage.clearLineage,
  };
};
